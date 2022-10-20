mod message;

use std::net::SocketAddr;

use babel::{
    network::{channel::tcp::TcpChannelParams, ConnectionID},
    protocol::{Context, Protocol, ProtocolID, SetupContext},
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

pub struct Kademlia {
    id: KadID,
    routing_table: RoutingTable,
}

impl Kademlia {
    pub fn new(k: u32) -> Self {
        let id = KadID::random();
        Self {
            id,
            routing_table: RoutingTable::new(k, id),
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
    }

    fn init(&mut self, mut ctx: Context<Self>) {
        ctx.create_channel(
            "tcp",
            TcpChannelParams {
                listen_addr: SocketAddr::from(([127, 0, 0, 1], 4500)),
            },
        );
    }
}

// Message Handlers
impl Kademlia {
    fn on_handshake(&mut self, mut ctx: Context<Self>, conn: ConnectionID, msg: Handshake) {
        log::info!("Received handshake from {}: {msg:#?}", conn);
        ctx.connect(conn.host);
        ctx.send_message(conn.host, &Handshake::new(self.id));
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
        ctx: Context<Self>,
        conn: ConnectionID,
        msg: FindNodeResponse,
    ) {
        log::info!("Received find node response from {}: {msg:#?}", conn);
    }

    fn on_find_value_request(
        &mut self,
        ctx: Context<Self>,
        conn: ConnectionID,
        msg: FindValueRequest,
    ) {
        log::info!("Received find value request from {}: {msg:#?}", conn);
    }

    fn on_find_value_response(
        &mut self,
        ctx: Context<Self>,
        conn: ConnectionID,
        msg: FindValueResponse,
    ) {
        log::info!("Received find value response from {}: {msg:#?}", conn);
    }

    fn on_store_request(&mut self, ctx: Context<Self>, conn: ConnectionID, msg: StoreRequest) {
        log::info!("Received store request from {}: {msg:#?}", conn);
    }
}
