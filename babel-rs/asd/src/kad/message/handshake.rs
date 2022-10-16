use babel::{
    protocol::{Protocol, ProtocolMessage, ProtocolMessageID},
    Deserialize, Serialize,
};

use crate::kad::{KadID, Kademlia};

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Handshake {
    pub id: KadID,
}

impl Handshake {
    pub fn new(id: KadID) -> Self {
        Self { id }
    }
}

impl Serialize for Handshake {
    fn serialize<B>(&self, buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        self.id.serialize(buf)?;
        Ok(())
    }
}

impl Deserialize for Handshake {
    fn deserialize<B>(buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let id = KadID::deserialize(buf)?;
        Ok(Self { id })
    }
}

impl ProtocolMessage for Handshake {
    const ID: ProtocolMessageID = Kademlia::ID.message(5);
}
