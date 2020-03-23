
import * as React from 'react';
import { Table } from 'component/antd';

interface IFlow {
  key: string;
  avr: number;
  pre1: number;
  pre5: number;
  pre15: number;
}

const flowColumns = [{
  title: '名称',
  dataIndex: 'key',
  key: 'name',
  sorter: (a: IFlow, b: IFlow) => a.key.charCodeAt(0) - b.key.charCodeAt(0),
  render(t: string) {
    return t === 'byteRejected' ? 'byteRejected(B/s)' : (t === 'byteIn' || t === 'byteOut' ? `${t}(KB/s)` : t);
  },
},
{
  title: '平均数',
  dataIndex: 'avr',
  key: 'partition_num',
  sorter: (a: IFlow, b: IFlow) => a.avr - b.avr,
},
{
  title: '前1分钟',
  dataIndex: 'pre1',
  key: 'byte_input',
  sorter: (a: IFlow, b: IFlow) => a.pre1 - b.pre1,
},
{
  title: '前5分钟',
  dataIndex: 'pre5',
  key: 'byte_output',
  sorter: (a: IFlow, b: IFlow) => a.pre5 - b.pre5,
},
{
  title: '前15分钟',
  dataIndex: 'pre15',
  key: 'message',
  sorter: (a: IFlow, b: IFlow) => a.pre15 - b.pre15,
}];

export interface IFlowInfo {
  byteIn: number[];
  byteOut: number[];
  byteRejected: number[];
  failedFetchRequest: number[];
  failedProduceRequest: number[];
  messageIn: number[];
  [key: string]: number[];
}

export class StatusGraghCom<T extends IFlowInfo> extends React.Component {
  public getData(): T {
    return null;
  }

  public render() {
    const statusData = this.getData();
    if (!statusData) return null;
    const data: any[] = [];
    Object.keys(statusData).map((key) => {
      const v = key === 'byteIn' || key === 'byteOut' ? statusData[key].map(i => (i / 1024).toFixed(2)) :
        statusData[key].map(i => i.toFixed(2));
      const obj = {
        key,
        avr: v[0],
        pre1: v[1],
        pre5: v[2],
        pre15: v[3],
      };
      data.push(obj);
    });
    return (
      <Table columns={flowColumns} dataSource={data} pagination={false} />
    );
  }
}