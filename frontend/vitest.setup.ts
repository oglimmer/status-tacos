// Minimal polyfills for tests running in Node where some globals may be missing

// Ensure a usable localStorage for libraries that expect browser APIs
// Some environments provide a stub that doesn't implement getItem/setItem.
const needsLocalStoragePolyfill =
  typeof globalThis['localStorage'] === 'undefined' ||
  typeof globalThis['localStorage']?.getItem !== 'function'

if (needsLocalStoragePolyfill) {
  const store = new Map<string, string>()
  globalThis['localStorage'] = {
    getItem: (key: string) => (store.has(key) ? store.get(key)! : null),
    setItem: (key: string, value: string) => {
      store.set(key, String(value))
    },
    removeItem: (key: string) => {
      store.delete(key)
    },
    clear: () => store.clear(),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size
    },
  }
}

// Some libraries probe for sessionStorage as well
const needsSessionStoragePolyfill =
  typeof globalThis['sessionStorage'] === 'undefined' ||
  typeof globalThis['sessionStorage']?.getItem !== 'function'

if (needsSessionStoragePolyfill) {
  const store = new Map<string, string>()
  globalThis['sessionStorage'] = {
    getItem: (key: string) => (store.has(key) ? store.get(key)! : null),
    setItem: (key: string, value: string) => {
      store.set(key, String(value))
    },
    removeItem: (key: string) => {
      store.delete(key)
    },
    clear: () => store.clear(),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size
    },
  }
}
