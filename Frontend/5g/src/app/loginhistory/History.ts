export interface LoginHistory {
  id: number;
  username: string;
  loginTime: string;     // LocalDateTime → string (ISO format)
  ipAddress: string;
  userAgent: string;
}