#![feature(drain_filter)]

use babel::{channel::tcp::TcpChannelFactory, ApplicationBuilder};
use kad::Kademlia;

pub mod kad;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    env_logger::init();

    let application = ApplicationBuilder::default()
        .register_channel("tcp", TcpChannelFactory)
        .register_protocol(Kademlia::new(20))
        .build()
        .await;

    application.run().await;

    Ok(())
}
