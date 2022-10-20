use std::net::SocketAddr;

use babel::network::{Deserialize, Serialize};

use super::KadID;

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Peer {
    pub id: KadID,
    pub addr: SocketAddr,
}

impl Peer {
    pub fn new(id: KadID, addr: SocketAddr) -> Self {
        Self { id, addr }
    }
}

impl std::fmt::Display for Peer {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}@{}", self.id, self.addr)
    }
}

impl Serialize for Peer {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        self.id.serialize(&mut buf)?;
        self.addr.serialize(buf)?;
        Ok(())
    }
}

impl Deserialize for Peer {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let id = KadID::deserialize(&mut buf)?;
        let addr = SocketAddr::deserialize(buf)?;
        Ok(Self { id, addr })
    }
}
