use babel::network::{Deserialize, Serialize};
use bytes::{Buf, BufMut};
use rand::RngCore;

use super::Distance;

#[derive(Clone, Copy, PartialEq, Eq, Hash)]
pub struct KadID([u8; 20]);

impl KadID {
    pub const LENGTH: usize = 20;

    pub fn new(id: [u8; Self::LENGTH]) -> Self {
        Self(id)
    }

    pub fn zero() -> Self {
        Self([0; Self::LENGTH])
    }

    pub fn random() -> Self {
        let mut rng = rand::thread_rng();
        let mut id = [0; Self::LENGTH];
        rng.fill_bytes(&mut id);
        Self(id)
    }

    pub fn random_with_cpl(base: &Self, cpl: u32) -> Self {
        let cpl = cpl as usize;
        let mut id = Self::random();
        let cpl_bytes = cpl / 8;
        let cpl_bits = cpl % 8;
        for i in 0..cpl_bytes {
            id.0[i] = base.0[i];
        }
        let mask = 1 << (7 - cpl_bits);
        id.0[cpl_bytes] = base.0[cpl_bytes] ^ mask;
        id
    }

    pub fn distance(&self, other: &Self) -> Distance {
        let mut distance = [0u8; Self::LENGTH];
        for i in 0..Self::LENGTH {
            distance[i] = self.0[i] ^ other.0[i];
        }
        Distance::new(distance)
    }

    pub fn cpl(&self, other: &Self) -> u32 {
        let mut cpl = 0;
        for (b1, b2) in self.0.iter().zip(other.0.iter()) {
            let b = b1 ^ b2;
            let lz = b.leading_zeros();
            cpl += lz;
            if lz < 8 {
                break;
            }
        }
        cpl
    }
}

impl std::fmt::Debug for KadID {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        struct Hex<'a>(&'a [u8]);
        impl std::fmt::Debug for Hex<'_> {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                for b in self.0 {
                    write!(f, "{:02x}", b)?;
                }
                Ok(())
            }
        }
        f.debug_tuple("KadID").field(&Hex(&self.0)).finish()
    }
}

impl std::fmt::Display for KadID {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        for b in self.0.iter() {
            write!(f, "{:02x}", b)?;
        }
        Ok(())
    }
}

impl Serialize for KadID {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: BufMut,
    {
        buf.put_slice(&self.0);
        Ok(())
    }
}

impl Deserialize for KadID {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: Buf,
    {
        let mut id = [0; Self::LENGTH];
        buf.copy_to_slice(&mut id);
        Ok(Self(id))
    }
}
