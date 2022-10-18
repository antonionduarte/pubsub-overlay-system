#![feature(io_error_other)]
#![feature(closure_lifetime_binder)]
#![feature(const_type_name)]

use std::net::SocketAddr;

use bytes::{Buf, BufMut};

mod error;

pub(crate) mod application;
pub mod channel;
pub mod ipc;
pub(crate) mod mailbox;
pub mod props;
pub mod protocol;
pub(crate) mod service;
pub mod wire;

pub use application::{Application, ApplicationBuilder};
pub use error::*;
pub use props::Properties;

slotmap::new_key_type! {
    pub struct ChannelID;
    pub struct LinkID;
    pub struct TimerID;
}

pub trait Serialize {
    fn serialize<B>(&self, buf: B) -> std::io::Result<()>
    where
        B: BufMut;
}

pub trait Deserialize: Sized {
    fn deserialize<B>(buf: B) -> std::io::Result<Self>
    where
        B: Buf;
}

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
