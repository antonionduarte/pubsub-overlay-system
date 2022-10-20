use std::net::SocketAddr;

use bytes::{Buf, BufMut};

use super::{Deserialize, Serialize};

impl Serialize for SocketAddr {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: BufMut,
    {
        match self {
            SocketAddr::V4(addr) => {
                buf.put_slice(&addr.ip().octets());
                buf.put_u16(addr.port());
            }
            SocketAddr::V6(_) => {
                panic!("Serializing V6 addresses is not supported");
            }
        }
        Ok(())
    }
}

impl Deserialize for SocketAddr {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: Buf,
    {
        let ip = buf.get_u32();
        let port = buf.get_u16();
        Ok(SocketAddr::new(u32::to_be_bytes(ip).into(), port))
    }
}

impl<T> Serialize for Vec<T>
where
    T: Serialize,
{
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: BufMut,
    {
        buf.put_u32(self.len() as u32);
        for item in self {
            item.serialize(&mut buf)?;
        }
        Ok(())
    }
}

impl<T> Deserialize for Vec<T>
where
    T: Deserialize,
{
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: Buf,
    {
        let len = buf.get_u32() as usize;
        let mut vec = Vec::with_capacity(len);
        for _ in 0..len {
            vec.push(T::deserialize(&mut buf)?);
        }
        Ok(vec)
    }
}
