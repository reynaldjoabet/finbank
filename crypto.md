## CryptoCurrency
Cryptocurrency is a digital or virtual currency that uses cryptography for security.There are thousands of different "cryptos" (like Ethereum, Solana, or USDC), and they all serve different purposes—some are meant to be money, others are for running smart contracts or apps.

Bitcoin was the very first cryptocurrency and the first real-world application of blockchain technology.

Bitcoin is a type of cryptocurrency, and cryptocurrency is powered by blockchain

A hash is essentially a digital fingerprint. In a blockchain, this fingerprinting process serves two main purposes:
- Each block contains its own data (like transactions), its own hash, and—critically—the hash of the previous block.
- Efficient Verification (Merkle Trees): 
1. Transaction A and B are hashed.
2. The hashes of A and B are hashed together to create a "Root Hash."
3. If any transaction at the bottom changes, the Root Hash at the top changes.


The miner who finds this "golden" hash gets to add the block to the chain and receives a Bitcoin reward. This is Proof of Work.

USDC (USD Coin) is a stablecoin.To have the speed of blockchain but the stability of the US Dollar. 1 USDC is always designed to equal $1.00

Imagine there is no mining. Alice has 1 Bitcoin.
- At 10:00 AM, she sends 1 BTC to Bob for a car.
- At 10:01 AM, she sends that same 1 BTC to Charlie for a watch.

Proof of Work creates an official "Clock." The first transaction to make it into a mined block is the "Truth." The second one becomes invalid because the money is already gone

It is cheaper to follow the rules and get the Bitcoin reward than it is to try and cheat the system

Bitcoin’s consensus algorithm is called Proof of Work (PoW).

Blockchain operates as a decentralized distributed database, with data stored across multiple computers, making it resistant to tampering. Transactions are validated through a consensus mechanism, ensuring agreement across the network. 

In blockchain technology, each transaction is grouped into blocks, which are then linked together, forming a secure and transparent chain. This structure guarantees data integrity and provides a tamper-proof record, making blockchain ideal for applications like cryptocurrencies and supply chain management.

A block is essentially a data packet divided into two main parts: the Header and the Body.
The Header (The "Metadata")

1. This is the small part (usually only 80 bytes) that the miners use for the hashing race. It contains:

- The Previous Block's Hash: The link to the page before it.
- The Merkle Root: A single hash that represents all the transactions in the body
- The Timestamp: When the block was created.
- The Nonce: The "Guess Number" the miner changed millions of times to find the winning hash.

2. The Body (The "Transaction List")

This is where the actual data lives. In Bitcoin, a block is currently limited to about 1MB to 4MB in size.

In Scala, to use members from a `package object`, you import them normally (e.g., `import foo._`). Scala 3, however, encourages package-level definitions, while Scala 2 uses `package objects` more. `For "givens" or implicits, you'll need to import explicitly` (e.g., `import foo.given`). They are in scope within the same package.

Private/Public Keys: Uses Asymmetric Cryptography to ensure only the owner of an asset can initiate a transfer.

Hashing: Each block contains a unique digital fingerprint (hash) of the previous block. This creates a back-linked chain. If a single bit of data is changed in an old block, its hash changes, breaking the entire chain that follows

Because the ledger is distributed across thousands of nodes, there is no Single Point of Failure

Once a transaction is confirmed by the majority, it is nearly impossible to alter. To change one record, an attacker would have to recalculate the hashes for every subsequent block across the majority of the network simultaneously.

ECDSA (Elliptic Curve Digital Signature Algorithm): The most common choice. Bitcoin and Ethereum specifically use the secp256k1 curve.

EdDSA (Edwards-curve Digital Signature Algorithm): A newer, faster, and more secure alternative used by blockchains like Solana and Cardano (specifically the Ed25519 curve).

In a blockchain, "mining" is essentially a brute-force search for a Nonce (a random number) that, when combined with the block's data, produces a SHA-256 hash starting with a specific number of zeros (the Difficulty).



`hashing is not enough to send packets securely across the wire`
A MAC solves the above problem
Message Authentication Code: Concept of combining a message with a secret key before hashing, meant to detect unauthorized alteration of message and digest
only whoever has secret key can create an acceptable digest
gives us integrity and authentication of bulk data

in MAC, if sender does hash(Key+Message),receiver must do same, otheriwse hashes will be different
This difference is where an HMAC comes into play
An HMAC is a standard way of combining message+secret key
eg HMAC,Poly1305,GCM and CCM are AEAD ciphers, they combine encryption and MAC in a single step

`HMAC(k,m)=H((k⊕opad)∥H((k⊕ipad)∥m))`

In hava,Whether you are using a hash-based MAC (HMAC) or a block-cipher-based MAC (like AES-CMAC), you always use this same class

[](https://www.youtube.com/watch?v=fzMIjWFYQl0)

Pseudorandom functions (PRFs) and pseudorandom permutations (PRPs) are two of the most fundamental primitives in modern cryptography

Key derivation. Often in cryptography we have a single random key k and we need to turn this into several random-looking keys (k1, k2, etc.) This happens within protocols like TLS, which (at least in version 1.3) has an entire tree of keys that it derives from a single master secret. PRFs, it turns out, are an excellent for this task. To “diversify” a single key into multiple keys, one can simply evaluate the PRF at a series of distinct points (say, k1 = PRF(k, 1), k2 = PRF(k, 2), and so on), and the result is a set of keys that are indistinguishable from random

pseudorandom functions (PRFs): these are functions that have output that is indistinguishable from a random function.


Stablecoins have become widely used in the cryptocurrency market by providing a stable and secure alternative to volatile digital assets. Unlike Bitcoin (BTC) and Ethereum (ETH), which fluctuate in value, stablecoins like USD Coin (USDC) and Tether (USDT) are pegged to real-world assets such as the U.S. dollar, making them a popular tool for traders and investors seeking price stability.

A stablecoin is a type of cryptocurrency designed to maintain a fixed value by being pegged to a reserve asset, such as the U.S. dollar, gold, or a basket of commodities. 

There is only 3 asymmetric algorithms:
- RSA,
- DSA (Digital Signature Algorithm)
- DH( Diffie-Hellman Key Exchange)

There are three possible operations in Asymmetic cryptography
- Encryption
- Signatures
- Key Exchanges

Of the three, only RSA can do encryption

In Asymmetric encryption, the public key is used for encryption while the private key for decryption

RSA is "commutative",math works in both directions for eencryption..can use private or public key to encrypt
if the public key is used for decryption, then there is no confidentiality

private key is used for create a signature and the public key is used to verify it

RSA Signature
- Hash data to be signed to create digest
- Encrypt digest with RSA private Key( result is the signature)
- Signature is attached to data

MACs are more efficient on bulk data
 Signatures are limited ti smaller data

HMAC is a way to create a MAC using a Hash function (like SHA-256).

AEAD (like AES-GCM) is a way to create a MAC using a Block Cipher (like AES).

In many systems, you have data that cannot be hidden (because a system needs to read it to route it), but it must not be changed.

Imagine you are sending an encrypted packet over a network:

- The Payload: The secret data (Encrypted + Authenticated).
- The Header: The IP addresses or Metadata (Cannot be encrypted because routers need to read them, but must be authenticated so a hacker can't change the destination).

AEAD allows you to include this "Associated Data" in the Tag calculation. If a hacker changes the unencrypted header, the Tag will fail, and you'll know the packet was tampered with

When you use an AEAD algorithm like AES-GCM, the function looks like this:
`Encrypt(Key,Nonce,Plaintext,AD)→(Ciphertext,Tag)`

The Tag is a result of both the secret Plaintext and the public AD.

- If you change the Ciphertext, the Tag breaks.
- If you change the AD, the Tag also breaks.

In a standard JWT, the "Header" is like the AD. It's not encrypted (anyone can base64-decode it), but it is part of the Signature.

If you change the alg (algorithm) in the header from `RS256` to none, the signature fails.

`AEAD` brings this exact same concept (Public but Protected) to the world of Symmetric Encryption.

- GCM uses Galois Field Multiplication. It treats your data as a massive polynomial and multiplies it by a secret key.

- `Poly1305` is a Polynomial Authenticator. It breaks your message into chunks, treats them as coefficients of a math equation (m1​x^2+m2​x+m3​), and solves it using a secret key `x`

1. Format the AD: Take the public data (AD) and pad it so it fits the block size (usually 16 bytes).

2. Start the Math: Feed the AD blocks into the polynomial/multiplication engine first.

3.Feed the Ciphertext: Feed the encrypted secret data in second.

4. Finalize: Add the lengths of both at the end and crunch the final number.

Because the AD is part of the initial math, the final Tag depends on both the public AD and the secret Ciphertext

XChaCha20-Poly1305
- The "X" stands for eXtended: It has a 192-bit Nonce (instead of 96-bit).
- Why it matters: With a 192-bit Nonce, you can safely generate nonces randomly without ever worrying about a collision

In a polynomial authenticator like GCM, the math looks something like this:
`Tag=(AD1​⋅Hn)+(AD2​⋅Hn−1)+⋯+(Cn​⋅H1)`

(Where `H` is the secret key and `C` is the ciphertext)

Because this is just a big sum of multiplications, a computer doesn't have to wait for the first block to finish before starting the second. If you have a multi-core CPU or a GPU, you can calculate the "fingerprint" of different parts of the message at the same time

### The "Key" is the Variable

In a high school equation like `y=3x2+2x+5`, the variable is `x`.
In `Poly1305`, the secret key `(r)` is that variable `x`

### Data becomes the Coefficients
Imagine you want to authenticate a message. We break the message into 16-byte chunks.
- `Chunk 1 (m1​): Hello` 
- `Chunk 2 (m2​): World!`

We turn these text chunks into large numbers. Now, we build a polynomial where these numbers are the coefficients:
`Tag=(m1​⋅r2)+(m2​⋅r1)+s`


(Where s is a second secret part of the key used to "mask" the result).