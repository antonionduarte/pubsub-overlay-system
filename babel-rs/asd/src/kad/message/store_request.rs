use babel::{
    network::{Deserialize, Serialize},
    protocol::{Protocol, ProtocolMessage, ProtocolMessageID},
};

use crate::kad::{KadID, Kademlia};

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct StoreRequest {
    pub key: KadID,
    pub value: Vec<u8>,
}

impl Serialize for StoreRequest {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        self.key.serialize(&mut buf)?;
        buf.put_u32(self.value.len() as u32);
        buf.put_slice(&self.value);
        Ok(())
    }
}

impl Deserialize for StoreRequest {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let key = KadID::deserialize(&mut buf)?;
        let value_len = buf.get_u32() as usize;
        let mut value = vec![0u8; value_len];
        buf.copy_to_slice(&mut value);
        Ok(Self { key, value })
    }
}

impl ProtocolMessage for StoreRequest {
    const ID: ProtocolMessageID = Kademlia::ID.message(6);
}
