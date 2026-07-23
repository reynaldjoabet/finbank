public enum EncryptionAlgorithm {
    // 1. Constants MUST come first
    AES_256_GCM("AES/GCM/NoPadding", 256, 12, 16),
    AES_128_GCM("AES/GCM/NoPadding", 128, 12, 16),
    AES_256_CBC("AES/CBC/PKCS5Padding", 256, 16, 0),
    CHACHA20_POLY1305("ChaCha20-Poly1305", 256, 12, 16),
    CHA_CHA20_POLY1305("ChaCha20-Poly1305", 128, 12, 128); 

    // 2. Fields, Constructors, and Methods follow
    private final String transformation;
    private final int keySize;
    private final int ivSize;
    private final int tagSize;

    EncryptionAlgorithm(String transformation, int keySize, int ivSize, int tagSize) {
        this.transformation = transformation;
        this.keySize = keySize;
        this.ivSize = ivSize;
        this.tagSize = tagSize;
    }

    public String getTransformation() { return transformation; }
    public int getKeySize() { return keySize; }
    public int getIvSize() { return ivSize; }
    public int getTagSize() { return tagSize; }   
}