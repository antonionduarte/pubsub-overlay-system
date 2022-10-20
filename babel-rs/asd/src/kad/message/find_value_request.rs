use babel::{
    network::{Deserialize, Serialize},
    protocol::{Protocol, ProtocolMessage, ProtocolMessageID},
};

use crate::kad::{KadID, Kademlia};

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct FindValueRequest {
    pub context: u32,
    pub key: KadID,
}

impl FindValueRequest {
    pub fn new(context: u32, key: KadID) -> Self {
        Self { context, key }
    }
}

impl Serialize for FindValueRequest {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        buf.put_u32(self.context);
        self.key.serialize(buf)?;
        Ok(())
    }
}

impl Deserialize for FindValueRequest {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let context = buf.get_u32();
        let key = KadID::deserialize(buf)?;
        Ok(Self { context, key })
    }
}

impl ProtocolMessage for FindValueRequest {
    const ID: ProtocolMessageID = Kademlia::ID.message(3);
}
