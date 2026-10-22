export interface AccountRecord {
  id: number;
  goodfishId: string;
  nickname: string;
  isValid: boolean;
  listenState: string;
  remark: string;
  lastCheckAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface AccountCreatePayload {
  cookie: string;
  remark: string;
}

export interface AccountUpdatePayload {
  nickname: string;
  remark: string;
  cookie?: string;
}

export interface AccountDetail extends AccountRecord {
  cookie: string;
}
