#![feature(io_error_other)]
#![feature(const_type_name)]

use std::net::SocketAddr;

use babel::{
    channel::tcp::TcpChannelFactory,
    protocol::{Context, Protocol, ProtocolID, ProtocolMessage, ProtocolMessageID},
    ApplicationBuilder, ChannelID, Deserialize, Properties, Serialize,
};

#[derive(Debug, Default)]
struct Handshake {
    kad_id: [u8; 20],
}

impl Serialize for Handshake {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        buf.put_slice(&self.kad_id);
        Ok(())
    }
}

impl Deserialize for Handshake {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let mut id = [0u8; 20];
        buf.copy_to_slice(&mut id);
        Ok(Self { kad_id: id })
    }
}

impl ProtocolMessage for Handshake {
    const ID: ProtocolMessageID = ProtocolMessageID::new(105);
}

struct TestProtocol;

impl Protocol for TestProtocol {
    const ID: ProtocolID = ProtocolID::new(100);

    const NAME: &'static str = "Kademlia";

    fn init(&mut self, mut ctx: babel::protocol::Context<Self>) {
        ctx.create_channel("tcp", Properties::builder().with_i16("port", 4500).build());
        ctx.register_message_handler(Self::on_handshake);
    }

    fn on_timer(&mut self, ctx: babel::protocol::Context<Self>, timer_id: babel::TimerID) {}

    fn on_connection_event(
        &mut self,
        ctx: babel::protocol::Context<Self>,
        channel_id: babel::ChannelID,
        event: babel::channel::ConnectionEvent,
    ) {
    }
}

impl TestProtocol {
    fn on_handshake(
        &mut self,
        mut ctx: Context<Self>,
        _: ChannelID,
        addr: SocketAddr,
        _: Handshake,
    ) {
        println!("received handshake from remote peer at : {addr}");
        ctx.send_message(addr, &Handshake::default());
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    env_logger::init();

    let application = ApplicationBuilder::default()
        .register_channel("tcp", TcpChannelFactory)
        .register_protocol(TestProtocol)
        .build()
        .await;

    application.run().await;

    Ok(())
}

/*
#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    env_logger::init();

    let listener = TcpListener::bind("0.0.0.0:4500").await?;
    let (stream, _remote) = listener.accept().await?;
    let (reader, writer) = stream.into_split();
    let (reader, writer) = (BufReader::new(reader), BufWriter::new(writer));
    let (mut encoder, mut decoder) = (
        wire::Encoder::<_, BabelMessage>::new(writer),
        wire::Decoder::<_, BabelMessage>::new(reader),
    );

    let mut handshake = match decoder.decode().await? {
        wire::Message::Control(ControlMessage::FirstHandshake(hs)) => hs,
        _ => panic!("expected to receive first handshake"),
    };
    println!("{handshake:?}");
    handshake
        .properties
        .insert("listen_address", [127, 0, 0, 1, 0x11, 0x94]);
    encoder
        .control(&ControlMessage::SecondHandshake(handshake))
        .await?;

    loop {
        let msg = decoder.decode().await?;
        println!("{msg:#?}");
    }
}
*/

/*
use std::{any::Any, time::Duration};

use babel::{
    protocol::{Context, Protocol, ProtocolID, ProtocolMessage, ProtocolMessageID},
    Deserialize, LinkID, Properties, Serialize,
};
use bytes::{Buf, BufMut};
use tokio::{io::AsyncReadExt, net::TcpListener};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    env_logger::init();

    let mut appbuilder = babel::ApplicationBuilder::new();
    appbuilder.register_protocol(TestProtocol);

    let mut app = appbuilder.build().await;
    app.run();

    return Ok(());
    let listener = TcpListener::bind("0.0.0.0:4500").await?;
    let (mut stream, remote) = listener.accept().await?;

    let msgsize = stream.read_i32().await?;
    let msgkind = stream.read_i8().await?;
    let opcode = stream.read_i32().await?;
    let magic = stream.read_i32().await?;
    println!("Message Size = {msgsize}");
    println!("Message Kind = {msgkind}");
    println!("Opcode = {opcode}");
    println!("Magic Number = {magic:X}");

    let mut buffer = [0u8; 1024];
    let attr_count = stream.read_i32().await?;
    for _ in 0..attr_count {
        let key_size = stream.read_i32().await?;
        stream.read_exact(&mut buffer[..key_size as usize]).await?;
        let key = std::str::from_utf8(&buffer[..key_size as usize]).expect("Invalid property key");
        print!("Key = {key}");
        let value_size = stream.read_i32().await?;
        stream
            .read_exact(&mut buffer[..value_size as usize])
            .await?;
        println!(" Size = {value_size}");
    }

    Ok(())
}
pub(crate) struct ProtocolMessageContainer {
    pub message_id: ProtocolMessageID,
    pub message: Box<dyn Any + Send>,
}

#[derive(Debug)]
struct HandshakeView<'p>(&'p Properties);

impl<'p> Serialize for HandshakeView<'p> {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: BufMut,
    {
        buf.put_i32(CONTROL_MAGIC_NUMBER);
        self.0.serialize(&mut buf)?;
        Ok(())
    }
}

impl<'p> HandshakeView<'p> {
    pub fn new(properties: &'p Properties) -> Self {
        HandshakeView(properties)
    }
}

#[derive(Debug, Clone)]
struct Handshake {
    magic_number: i32,
    properties: Properties,
}

impl Deserialize for Handshake {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: Buf,
    {
        let magic_number = buf.get_i32();
        let properties = Properties::deserialize(&mut buf)?;
        Ok(Handshake {
            magic_number,
            properties,
        })
    }
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq, Hash)]
struct Heartbeat;

impl Serialize for Heartbeat {
    fn serialize<B>(&self, buf: B) -> std::io::Result<()>
    where
        B: BufMut,
    {
        Ok(())
    }
}

impl Deserialize for Heartbeat {
    fn deserialize<B>(buf: B) -> std::io::Result<Self>
    where
        B: Buf,
    {
        Ok(Heartbeat)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
struct InvalidAttribute {
    magic_number: i32,
}

impl Default for InvalidAttribute {
    fn default() -> Self {
        InvalidAttribute {
            magic_number: CONTROL_MAGIC_NUMBER,
        }
    }
}

impl Serialize for InvalidAttribute {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: BufMut,
    {
        buf.put_i32(self.magic_number);
        Ok(())
    }
}

impl Deserialize for InvalidAttribute {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: Buf,
    {
        let magic_number = buf.get_i32();
        Ok(InvalidAttribute { magic_number })
    }
}

#[derive(Debug, Clone)]
enum ControlMessage {
    Heartbeat(Heartbeat),
    FirstHandshake(Handshake),
    SecondHandshake(Handshake),
    InvalidAttribute(InvalidAttribute),
}

struct ApplicationMessage;

enum WireMessage {
    ControlMessage(ControlMessage),
    ApplicationMessage(ApplicationMessage),
}

struct TestMessage;

impl Serialize for TestMessage {
    fn serialize<B>(&self, buf: B) -> std::io::Result<()>
    where
        B: BufMut,
    {
        Ok(())
    }
}

impl Deserialize for TestMessage {
    fn deserialize<B>(buf: B) -> std::io::Result<Self>
    where
        B: Buf,
    {
        Ok(TestMessage)
    }
}

impl ProtocolMessage for TestMessage {
    const ID: ProtocolMessageID = ProtocolMessageID::new(1);
}

struct TestProtocol;

impl Protocol for TestProtocol {
    const ID: ProtocolID = ProtocolID::new(1);

    const NAME: &'static str = std::any::type_name::<Self>();

    fn init(&mut self, mut ctx: Context<Self>) {
        log::info!("Creating timer");
        ctx.create_periodic_timer(Duration::from_secs(2), Duration::from_secs(1));
    }

    fn on_timer(&mut self, ctx: Context<Self>, timer_id: babel::TimerID) {
        log::info!("Timer fired: {:?}", timer_id);
    }
}

impl TestProtocol {
    fn on_test_message(&mut self, ctx: Context<Self>, link_id: LinkID, message: TestMessage) {
        todo!()
    }
}
*/
