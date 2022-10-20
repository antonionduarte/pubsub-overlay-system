use std::{collections::HashMap, net::SocketAddr};

use bytes::{Buf, BufMut};
use thiserror::Error;

use crate::network::{Deserialize, Serialize};

#[derive(Debug, Error)]
#[error("invalid property value for key {key}, exptected a {expected}")]
pub struct InvalidPropertyValue {
    key: String,
    expected: &'static str,
}

#[derive(Debug, Default, Clone)]
pub struct Properties(HashMap<String, Vec<u8>>);

#[derive(Debug, Default)]
pub struct PropertiesBuilder(Properties);

impl InvalidPropertyValue {
    fn new(key: impl Into<String>, expected: &'static str) -> Self {
        Self {
            key: key.into(),
            expected,
        }
    }
}

impl Serialize for Properties {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: BufMut,
    {
        buf.put_i32(self.0.len() as i32);
        for (key, value) in self.0.iter() {
            buf.put_i32(key.len() as i32);
            buf.put_slice(key.as_bytes());
            buf.put_i32(value.len() as i32);
            buf.put_slice(value);
        }
        Ok(())
    }
}

impl Deserialize for Properties {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: Buf,
    {
        let mut map = HashMap::new();
        let len = buf.get_i32();
        for _ in 0..len {
            let key_len = buf.get_i32();
            let key = buf.copy_to_bytes(key_len as usize);
            let value_len = buf.get_i32();
            let value = buf.copy_to_bytes(value_len as usize);
            map.insert(
                String::from_utf8(key.to_vec()).map_err(|e| std::io::Error::other(e))?,
                value.to_vec(),
            );
        }
        Ok(Properties(map))
    }
}

impl Properties {
    pub fn new() -> Self {
        Default::default()
    }

    pub fn builder() -> PropertiesBuilder {
        PropertiesBuilder::default()
    }

    pub fn insert(&mut self, key: impl Into<String>, value: impl Into<Vec<u8>>) {
        self.0.insert(key.into(), value.into());
    }

    pub fn insert_addr(&mut self, key: impl Into<String>, value: SocketAddr) {
        let mut data = Vec::new();
        value.serialize(&mut data).unwrap();
        self.insert(key, data);
    }

    pub fn insert_i16(&mut self, key: impl Into<String>, value: i16) {
        self.0.insert(key.into(), i16::to_be_bytes(value).into());
    }

    pub fn insert_u16(&mut self, key: impl Into<String>, value: u16) {
        self.0.insert(key.into(), u16::to_be_bytes(value).into());
    }

    pub fn insert_i32(&mut self, key: impl Into<String>, value: i32) {
        self.0.insert(key.into(), i32::to_be_bytes(value).into());
    }

    pub fn insert_u32(&mut self, key: impl Into<String>, value: u32) {
        self.0.insert(key.into(), u32::to_be_bytes(value).into());
    }

    pub fn get(&self, key: impl AsRef<str>) -> Option<&[u8]> {
        self.0.get(key.as_ref()).map(|v| v.as_slice())
    }

    pub fn get_addr(
        &self,
        key: impl AsRef<str>,
    ) -> Option<Result<SocketAddr, InvalidPropertyValue>> {
        let key = key.as_ref();
        let value = self.get(key)?;
        Some(
            SocketAddr::deserialize(&value[..])
                .map_err(|_| InvalidPropertyValue::new(key, "SocketAddr")),
        )
    }

    pub fn get_i16(&self, key: impl AsRef<str>) -> Option<Result<i16, InvalidPropertyValue>> {
        let key = key.as_ref();
        let value = self.get(key)?;
        if value.len() != 2 {
            return Some(Err(InvalidPropertyValue::new(key, "i16")));
        }
        Some(Ok(i16::from_be_bytes(value.try_into().unwrap())))
    }

    pub fn get_u16(&self, key: impl AsRef<str>) -> Option<Result<u16, InvalidPropertyValue>> {
        let key = key.as_ref();
        let value = self.get(key)?;
        if value.len() != 2 {
            return Some(Err(InvalidPropertyValue::new(key, "u16")));
        }
        Some(Ok(u16::from_be_bytes(value.try_into().unwrap())))
    }

    pub fn get_i32(&self, key: impl AsRef<str>) -> Option<Result<i32, InvalidPropertyValue>> {
        let key = key.as_ref();
        let value = self.get(key)?;
        if value.len() != 4 {
            return Some(Err(InvalidPropertyValue::new(key, "i32")));
        }
        Some(Ok(i32::from_be_bytes(value.try_into().unwrap())))
    }

    pub fn get_u32(&self, key: impl AsRef<str>) -> Option<Result<u32, InvalidPropertyValue>> {
        let key = key.as_ref();
        let value = self.get(key)?;
        if value.len() != 4 {
            return Some(Err(InvalidPropertyValue::new(key, "u32")));
        }
        Some(Ok(u32::from_be_bytes(value.try_into().unwrap())))
    }
}

impl PropertiesBuilder {
    pub fn with_i16(mut self, key: impl Into<String>, value: i16) -> Self {
        self.0.insert_i16(key, value);
        self
    }

    pub fn build(self) -> Properties {
        self.0
    }
}
