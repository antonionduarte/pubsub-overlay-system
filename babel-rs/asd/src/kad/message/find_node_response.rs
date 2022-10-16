use babel::{
    protocol::{Protocol, ProtocolMessage, ProtocolMessageID},
    Deserialize, Serialize,
};

use crate::kad::{Kademlia, Peer};

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct FindNodeResponse {
    pub context: u32,
    pub peers: Vec<Peer>,
}

impl FindNodeResponse {
    pub fn new(context: u32, peers: Vec<Peer>) -> Self {
        Self { context, peers }
    }
}

impl Serialize for FindNodeResponse {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        buf.put_u32(self.context);
        buf.put_u32(self.peers.len() as u32);
        for peer in &self.peers {
            peer.serialize(&mut buf)?;
        }
        Ok(())
    }
}

impl Deserialize for FindNodeResponse {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let context = buf.get_u32();
        let peers_len = buf.get_u32() as usize;
        let mut peers = Vec::with_capacity(peers_len);
        for _ in 0..peers_len {
            peers.push(Peer::deserialize(&mut buf)?);
        }
        Ok(Self { context, peers })
    }
}

impl ProtocolMessage for FindNodeResponse {
    const ID: ProtocolMessageID = Kademlia::ID.message(2);
}
