export type PlatformBaseline = {
  status: string
  serverTime: string
  resourceProtection: {
    queryTimeoutSeconds: number
    federatedQueryTimeoutSeconds: number
    maxBatchSize: number
    maxResultRows: number
    maxConcurrentQueries: number
  }
}
