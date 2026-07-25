export interface JwtPayload {
  sub: string;
  authorities?: string[];
  iat?: number;
  exp?: number;
  jti?: string;
}
