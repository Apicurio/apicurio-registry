import { vi } from 'vitest';

vi.mock('../../typescript-sdk/lib/generated-client/apicurioRegistryClient.js', () => ({
  ApicurioRegistryClient: class MockClient {},
  createApicurioRegistryClient: vi.fn(() => ({}))
}));
