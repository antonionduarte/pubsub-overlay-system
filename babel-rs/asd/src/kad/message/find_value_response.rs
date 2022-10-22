use babel::{
    network::{Deserialize, Serialize},
    protocol::{Protocol, ProtocolMessage, ProtocolMessageID},
};

use crate::kad::{Kademlia, Peer};

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct FindValueResponse {
    pub context: u64,
    pub value: Option<Vec<u8>>,
    pub peers: Vec<Peer>,
}

impl FindValueResponse {
    pub fn new(context: u64, value: Option<Vec<u8>>, peers: Vec<Peer>) -> Self {
        Self {
            context,
            value,
            peers,
        }
    }
}

impl Serialize for FindValueResponse {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        buf.put_u64(self.context);
        buf.put_u8(self.value.is_some() as u8);
        if let Some(value) = &self.value {
            buf.put_u32(value.len() as u32);
            buf.put_slice(value);
        }
        buf.put_u32(self.peers.len() as u32);
        for peer in &self.peers {
            peer.serialize(&mut buf)?;
        }
        Ok(())
    }
}

impl Deserialize for FindValueResponse {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let context = buf.get_u64();
        let value = if buf.get_u8() == 1 {
            let value_len = buf.get_u32() as usize;
            let mut value = vec![0u8; value_len];
            buf.copy_to_slice(&mut value);
            Some(value)
        } else {
            None
        };
        let peers_len = buf.get_u32() as usize;
        let mut peers = Vec::with_capacity(peers_len);
        for _ in 0..peers_len {
            peers.push(Peer::deserialize(&mut buf)?);
        }
        Ok(Self {
            context,
            value,
            peers,
        })
    }
}

impl ProtocolMessage for FindValueResponse {
    const ID: ProtocolMessageID = Kademlia::ID.message(4);
}
