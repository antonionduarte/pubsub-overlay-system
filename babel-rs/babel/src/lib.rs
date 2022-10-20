#![feature(io_error_other)]
#![feature(closure_lifetime_binder)]
#![feature(const_type_name)]

mod error;

pub(crate) mod application;
pub mod ipc;
pub(crate) mod mailbox;
pub mod network;
pub mod props;
pub mod protocol;
pub(crate) mod timer;

pub use application::{Application, ApplicationBuilder};
pub use error::*;
pub use props::Properties;

slotmap::new_key_type! {
    pub struct ChannelID;
    pub struct LinkID;
    pub struct TimerID;
}
