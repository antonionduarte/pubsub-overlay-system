mod message;
use std::net::SocketAddr;

use babel::{
    protocol::{Context, Protocol, ProtocolID, SetupContext},
    ChannelID, Properties,
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
        ctx.create_channel("tcp", Properties::builder().with_i16("port", 4500).build());
    }
}

// Message Handlers
impl Kademlia {
    fn on_handshake(
        &mut self,
        mut ctx: Context<Self>,
        _: ChannelID,
        addr: SocketAddr,
        msg: Handshake,
    ) {
        log::info!("Received handshake from {}: {msg:#?}", addr);
        ctx.send_message(addr, &Handshake::new(self.id));
    }

    fn on_find_node_request(
        &mut self,
        mut ctx: Context<Self>,
        _: ChannelID,
        addr: SocketAddr,
        msg: FindNodeRequest,
    ) {
        log::info!("Received find node request from {}: {msg:#?}", addr);

        let closest_peers = self.routing_table.closest(&msg.target);
        ctx.send_message(addr, &FindNodeResponse::new(msg.context, closest_peers));
    }

    fn on_find_node_response(
        &mut self,
        ctx: Context<Self>,
        channel_id: ChannelID,
        addr: SocketAddr,
        msg: FindNodeResponse,
    ) {
        log::info!("Received find node response from {}: {msg:#?}", addr);
    }

    fn on_find_value_request(
        &mut self,
        ctx: Context<Self>,
        channel_id: ChannelID,
        addr: SocketAddr,
        msg: FindValueRequest,
    ) {
        log::info!("Received find value request from {}: {msg:#?}", addr);
    }

    fn on_find_value_response(
        &mut self,
        ctx: Context<Self>,
        channel_id: ChannelID,
        addr: SocketAddr,
        msg: FindValueResponse,
    ) {
        log::info!("Received find value response from {}: {msg:#?}", addr);
    }

    fn on_store_request(
        &mut self,
        ctx: Context<Self>,
        channel_id: ChannelID,
        addr: SocketAddr,
        msg: StoreRequest,
    ) {
        log::info!("Received store request from {}: {msg:#?}", addr);
    }
}
