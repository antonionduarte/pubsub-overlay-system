#![feature(drain_filter)]
#![feature(duration_constants)]

use std::net::SocketAddr;

use babel::{network::channel::tcp::TcpChannelFactory, ApplicationBuilder};
use clap::Parser;
use kad::{KadParams, Kademlia};
use tokio::{
    io::{AsyncBufReadExt, BufReader},
    signal::unix::SignalKind,
};

pub mod kad;

#[derive(Debug, Parser)]
struct Args {
    /// The port to listen on
    #[arg(long)]
    port: u16,

    /// Kademlia K parameter
    #[arg(long, default_value_t = 20)]
    kad_k: u32,

    /// Kademlia alpha parameter
    #[arg(long, default_value_t = 3)]
    kad_alpha: u32,

    #[arg(long)]
    kad_bootstrap: Option<SocketAddr>,
}

#[tokio::main(flavor = "current_thread")]
async fn main() -> anyhow::Result<()> {
    env_logger::init();

    let args = Args::parse();

    let application = ApplicationBuilder::default()
        .register_channel("tcp", TcpChannelFactory)
        .register_protocol(Kademlia::new(
            KadParams::new(args.kad_k, args.kad_alpha),
            SocketAddr::from(([127, 0, 0, 1], args.port)),
            args.kad_bootstrap,
        ))
        .build()
        .await;

    let notification_sender = application.notification_sender();
    let handle = tokio::spawn(async move { application.run().await });

    let stdin = BufReader::new(tokio::io::stdin());

    let mut signal = tokio::signal::unix::signal(SignalKind::terminate())?;
    let mut lines = stdin.lines();
    
    loop {
        let line = tokio::select! {
            Ok(Some(line)) = lines.next_line() => line,
            _ = signal.recv() => break,
            else => break,
        };

        let line = line.trim();
        if line.is_empty() {
            continue;
        }

        let mut parts = line.split_whitespace().collect::<Vec<_>>();
        if parts.is_empty() {
            continue;
        }

        let command = parts.remove(0);
        match command {
            "rt" => notification_sender.send(kad::PrintRtNotification),
            "closest" => {}
            "exit" => {
                break;
            }
            _ => {
                println!("Unknown command: {}", command);
            }
        }
    }

    handle.abort();

    Ok(())
}
