use babel::{
    protocol::{Protocol, ProtocolMessage, ProtocolMessageID},
    Deserialize, Serialize,
};

use crate::kad::{KadID, Kademlia};

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct FindNodeRequest {
    pub context: u32,
    pub target: KadID,
}

impl FindNodeRequest {
    pub fn new(context: u32, target: KadID) -> Self {
        Self { context, target }
    }
}

impl Serialize for FindNodeRequest {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        buf.put_u32(self.context);
        self.target.serialize(buf)?;
        Ok(())
    }
}

impl Deserialize for FindNodeRequest {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let context = buf.get_u32();
        let target = KadID::deserialize(buf)?;
        Ok(Self { context, target })
    }
}

impl ProtocolMessage for FindNodeRequest {
    const ID: ProtocolMessageID = Kademlia::ID.message(1);
}
