const { webcrypto } = require('crypto');
global.crypto = webcrypto;
globalThis.crypto = webcrypto;
