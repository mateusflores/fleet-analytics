export interface JwtPayload {
  sub: string;
  authorities?: string[];
  userId?: string;
  iat?: number;
  exp?: number;
  jti?: string;
}
