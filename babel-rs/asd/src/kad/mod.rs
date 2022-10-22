mod message;

use std::{collections::HashMap, net::SocketAddr, time::Duration};

use babel::{
    network::{channel::tcp::TcpChannelParams, ConnectionID},
    protocol::{Context, Protocol, ProtocolID, SetupContext},
    timer::TimerID,
};

pub use message::*;
mod kad_id;
pub use kad_id::*;
mod distance;
pub use distance::*;
mod peer;
pub use peer::*;
mod bucket;
pub use bucket::*;
mod routing_table;
pub use routing_table::*;
mod query;
pub use query::*;
mod addrbook;
pub use addrbook::*;
mod ipc;
pub use ipc::*;

#[derive(Debug, Clone)]
pub struct KadParams {
    pub k: u32,
    pub alpha: u32,
}

impl KadParams {
    pub fn new(k: u32, alpha: u32) -> Self {
        Self { k, alpha }
    }
}

pub struct Kademlia {
    local_id: KadID,
    params: KadParams,
    routing_table: RoutingTable,
    address_book: AddrBook,
    query_manager: QueryManager,
    query_results: Vec<QueryResult>,
    listen_addr: SocketAddr,
    bootstrap: Option<SocketAddr>,
}

impl Kademlia {
    pub fn new(params: KadParams, listen_addr: SocketAddr, bootstrap: Option<SocketAddr>) -> Self {
        let local_id = KadID::random();
        Self {
            local_id,
            params: params.clone(),
            routing_table: RoutingTable::new(params.k, local_id),
            address_book: Default::default(),
            query_manager: QueryManager::new(params, local_id),
            query_results: Default::default(),
            listen_addr,
            bootstrap,
        }
    }
}

impl Protocol for Kademlia {
    const ID: ProtocolID = ProtocolID::new(100);

    const NAME: &'static str = "Kademlia";

    fn setup(&mut self, mut ctx: SetupContext<Self>) {
        // Register message handlers
        ctx.message_handler(Self::on_handshake);
        ctx.message_handler(Self::on_find_node_request);
        ctx.message_handler(Self::on_find_node_response);
        ctx.message_handler(Self::on_find_value_request);
        ctx.message_handler(Self::on_find_value_response);
        ctx.message_handler(Self::on_store_request);

        // Register notification handlers
        ctx.notification_handler(Self::on_print_routing_table);
    }

    fn init(&mut self, mut ctx: Context<Self>) {
        ctx.create_channel(
            "tcp",
            TcpChannelParams {
                listen_addr: self.listen_addr,
            },
        );

        if let Some(bootstrap) = self.bootstrap {
            ctx.connect(bootstrap);
        }

        ctx.create_periodic_timer(Duration::SECOND * 2, Duration::SECOND * 4);
    }

    fn on_timer(&mut self, mut ctx: Context<Self>, _timer_id: TimerID) {
        let query_target = self.local_id;
        let (query_manager, mut qio) = self.create_query_io(&mut ctx);
        query_manager.query(
            &mut qio,
            QueryDescriptor::new(QueryKind::FindNode, query_target),
        );
        query_manager.check_timeouts(&mut qio);

        for result in self.query_results.drain(..) {}
    }

    fn on_connection_event(
        &mut self,
        mut ctx: Context<Self>,
        event: babel::network::ConnectionEvent,
    ) {
        match event.kind {
            babel::network::ConnectionEventKind::ConnectionUp => {
                if event.connection.direction == babel::network::ConnectionDirection::Outgoing {
                    let handshake = Handshake::new(self.local_id);
                    ctx.send_message(event.connection.host, &handshake);
                }
            }
            babel::network::ConnectionEventKind::ConnectionDown
            | babel::network::ConnectionEventKind::ConnectionFailed => {
                if let Some(peer_id) = self.address_book.host_to_id(&event.connection.host) {
                    log::info!(
                        "Connection to peer {} lost, removing from routing table",
                        peer_id
                    );
                    self.routing_table.remove(&peer_id);
                }
            }
        }
    }
}

// Message Handlers
impl Kademlia {
    fn on_handshake(&mut self, mut ctx: Context<Self>, conn: ConnectionID, msg: Handshake) {
        log::info!("Received handshake from {}: {msg:#?}", conn);
        ctx.connect(conn.host);
        self.address_book.insert(msg.id, conn.host);
        self.routing_table.insert(Peer::new(msg.id, conn.host));
    }

    fn on_find_node_request(
        &mut self,
        mut ctx: Context<Self>,
        conn: ConnectionID,
        msg: FindNodeRequest,
    ) {
        log::info!("Received find node request from {}: {msg:#?}", conn);

        let closest_peers = self.routing_table.closest(&msg.target);
        ctx.send_message(
            conn.host,
            &FindNodeResponse::new(msg.context, closest_peers),
        );
    }

    fn on_find_node_response(
        &mut self,
        mut ctx: Context<Self>,
        conn: ConnectionID,
        msg: FindNodeResponse,
    ) {
        log::info!("Received find node response from {}: {msg:#?}", conn);
        let query_id = QueryID::from(msg.context);
        let peer_id = self.address_book.host_to_id(&conn.host).unwrap();
        let (query_manager, mut qio) = self.create_query_io(&mut ctx);
        query_manager.on_find_node_response(&mut qio, query_id, peer_id, msg.peers);
    }

    fn on_find_value_request(
        &mut self,
        mut ctx: Context<Self>,
        conn: ConnectionID,
        msg: FindValueRequest,
    ) {
        log::info!("Received find value request from {}: {msg:#?}", conn);
        ctx.send_message(
            conn.host,
            &FindValueResponse::new(msg.context, None, self.routing_table.closest(&msg.key)),
        );
    }

    fn on_find_value_response(
        &mut self,
        mut ctx: Context<Self>,
        conn: ConnectionID,
        msg: FindValueResponse,
    ) {
        log::info!("Received find value response from {}: {msg:#?}", conn);
        let query_id = QueryID::from(msg.context);
        let peer_id = self.address_book.host_to_id(&conn.host).unwrap();
        let (query_manager, mut qio) = self.create_query_io(&mut ctx);
        query_manager.on_find_value_response(&mut qio, query_id, peer_id, msg.peers, msg.value);
    }

    fn on_store_request(&mut self, ctx: Context<Self>, conn: ConnectionID, msg: StoreRequest) {
        log::info!("Received store request from {}: {msg:#?}", conn);
    }
}

// IPC Handlers
impl Kademlia {
    fn on_print_routing_table(&mut self, _: Context<Self>, _: ProtocolID, _: &PrintRtNotification) {
        println!("Routing Table:\n{}", self.routing_table);
    }
}

impl Kademlia {
    fn create_query_io<'a, 'p: 'a>(
        &'a mut self,
        ctx: &'a mut Context<'p, Self>,
    ) -> (&'a mut QueryManager, KadQueryIO<'p, 'a>) {
        let qio = KadQueryIO::new(
            ctx,
            &self.routing_table,
            &mut self.address_book,
            &mut self.query_results,
        );
        (&mut self.query_manager, qio)
    }
}

struct KadQueryIO<'p, 'a> {
    ctx: &'a mut Context<'p, Kademlia>,
    routing_table: &'a RoutingTable,
    address_book: &'a mut AddrBook,
    query_results: &'a mut Vec<QueryResult>,
}

impl<'p, 'a> KadQueryIO<'p, 'a> {
    fn new(
        ctx: &'a mut Context<'p, Kademlia>,
        routing_table: &'a RoutingTable,
        address_book: &'a mut AddrBook,
        query_results: &'a mut Vec<QueryResult>,
    ) -> Self {
        Self {
            ctx,
            routing_table,
            address_book,
            query_results,
        }
    }
}

impl<'p, 'a> QueryIO for KadQueryIO<'p, 'a> {
    fn discover(&mut self, peer: &Peer) {
        self.address_book.insert_peer(peer);
    }

    fn find_closest(&self, target: &KadID) -> Vec<Peer> {
        self.routing_table.closest(target)
    }

    fn send_find_node(&mut self, qid: QueryID, peer: &KadID, target: &KadID) {
        let context = u64::from(qid);
        let host = match self.address_book.id_to_host(peer) {
            Some(host) => host,
            None => {
                log::warn!("Failed to send find node request to {}: unknown peer", peer);
                return;
            }
        };
        self.ctx.connect(host);
        self.ctx
            .send_message(host, &FindNodeRequest::new(context, *target));
    }

    fn send_find_value(&mut self, qid: QueryID, peer: &KadID, target: &KadID) {
        let context = u64::from(qid);
        let host = match self.address_book.id_to_host(peer) {
            Some(host) => host,
            None => {
                log::warn!(
                    "Failed to send find value request to {}: unknown peer",
                    peer
                );
                return;
            }
        };
        self.ctx.connect(host);
        self.ctx
            .send_message(host, &FindValueRequest::new(context, *target));
    }

    fn query_result(&mut self, result: QueryResult) {
        self.query_results.push(result);
    }
}
