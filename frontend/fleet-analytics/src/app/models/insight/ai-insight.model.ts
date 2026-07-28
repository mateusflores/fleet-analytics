export type AiInsightType = 'WARNING' | 'INFO' | 'CRITICAL';

export interface AiInsight {
  title: string;
  description: string;
  type: AiInsightType;
  createdAt?: string;
}
