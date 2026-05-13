import { request } from '@/utils/request';

// 操作记录
export interface OperationLog {
  id: number;
  xianyuAccountId: number;
  operationType: string;
  operationModule: string;
  operationDesc: string;
  operationStatus: number;
  targetType?: string;
  targetId?: string;
  requestParams?: string;
  responseResult?: string;
  errorMessage?: string;
  ipAddress?: string;
  userAgent?: string;
  durationMs?: number;
  createTime: number;
}

// 查询操作记录请求
export interface QueryLogsRequest {
  accountId: number;
  operationType?: string;
  operationModule?: string;
  operationStatus?: number;
  page?: number;
  pageSize?: number;
}

// 查询操作记录响应
export interface QueryLogsResponse {
  logs: OperationLog[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface RuntimeLogResponse {
  file: string;
  lastModified: number;
  size: number;
  lines: string[];
  full?: boolean;
}

export interface RuntimeLogFile {
  file: string;
  lastModified: number;
  size: number;
}

export interface RuntimeLogFilesResponse {
  files: RuntimeLogFile[];
}

// 查询操作记录
export function queryOperationLogs(data: QueryLogsRequest) {
  return request<QueryLogsResponse>({
    url: '/operation-log/query',
    method: 'POST',
    data
  });
}

// 删除旧日志
export function deleteOldLogs(days: number) {
  return request<number>({
    url: '/operation-log/deleteOld',
    method: 'POST',
    data: { days }
  });
}

// 查询软件运行日志
export function queryRuntimeLogFiles() {
  return request<RuntimeLogFilesResponse>({
    url: '/operation-log/runtime/files',
    method: 'POST',
    data: {}
  });
}

// 查询软件运行日志
export function queryRuntimeLogs(data: { file?: string; lines?: number; full?: boolean }) {
  return request<RuntimeLogResponse>({
    url: '/operation-log/runtime',
    method: 'POST',
    data
  });
}

// 清空软件运行日志
export function clearRuntimeLogs(data: { file?: string }) {
  return request<Pick<RuntimeLogResponse, 'file' | 'lastModified' | 'size'>>({
    url: '/operation-log/runtime/clear',
    method: 'POST',
    data
  });
}
