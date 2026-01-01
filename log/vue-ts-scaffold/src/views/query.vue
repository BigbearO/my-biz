<template>
  <div class="log-query-container">
    <el-form :model="queryForm" size="small" :inline="true" label-width="100px" @submit.prevent="onSubmit">
      <!-- 日志等级 -->
      <el-form-item label="日志等级">
        <el-select class="query-item" v-model="queryForm.level"
                   clearable placeholder="日志等级">
          <el-option label="INFO" value="info"/>
          <el-option label="WARN" value="warn"/>
          <el-option label="ERROR" value="error"/>
          <el-option label="DEBUG" value="debug"/>
        </el-select>
      </el-form-item>

      <!-- 应用名称 -->
      <el-form-item label="应用名称">
        <el-input class="query-item" v-model="queryForm.app"/>
      </el-form-item>

      <!-- 应用环境 -->
      <el-form-item label="应用环境">
        <el-select class="query-item" v-model="queryForm.env"
                   clearable placeholder="应用环境">
          <el-option label="dev" value="dev"/>
          <el-option label="test" value="test"/>
          <el-option label="prod" value="prod"/>
        </el-select>
      </el-form-item>

      <!-- 追踪码 -->
      <el-form-item label="追踪码">
        <el-input class="query-item" v-model="queryForm.traceId"/>
      </el-form-item>

      <!-- 时间范围 -->
      <el-form-item label="时间范围">
        <div class="query-item">
          <el-date-picker v-model="timeRange"
                          :locale="locale"
                          type="datetimerange"
                          range-separator="至"
                          start-placeholder="开始时间"
                          end-placeholder="结束时间"
                          value-format="YYYY-MM-DD HH:mm:ss"
          />
        </div>
      </el-form-item>
      <div style="margin-left:2%">
        <el-button @click="logTimeSelect(10)">10分钟内</el-button>
        <el-button @click="logTimeSelect(30)">30分钟内</el-button>
        <el-button @click="logTimeSelect(60)">60分钟内</el-button>
        <el-button @click="logTimeSelect(6*60)">6小时内</el-button>
        <el-button @click="logTimeSelect(24*60)">24小时内</el-button>
      </div>

      <br/>
      <!-- 多个 message 条件 -->
      <el-form-item label="日志内容">
        <el-button @click="addMessage">添加条件</el-button>
        <br/>
        <!--        todo 优化 改成用灰色的一行显示-->
        <div v-for="(item, index) in queryForm.messages" :key="index" class="message-item">
          <el-input v-model="item.value" placeholder="内容"/>
          <el-select v-model="item.op" placeholder="逻辑" style="width: 50%">
            <el-option label="AND" value="and"/>
            <el-option label="OR" value="or"/>
            <el-option label="NOT" value="not"/>
          </el-select>
          <el-button @click="removeMessage(index)">删除</el-button>
        </div>

      </el-form-item>

      <!-- 查询按钮 -->
      <el-form-item>
        <el-button type="primary" @click="onSubmit">查询</el-button>
      </el-form-item>
    </el-form>

    <div style="height: 590px;overflow-y: auto;">
      <!-- 查询结果表格 -->
      <el-table :data="tableData" size="small" border style="width: 100%">

        <el-table-column prop="@timestamp" label="时间" width="160"/>
        <el-table-column prop="app" label="应用名称" width="100"/>
        <el-table-column prop="env" label="应用环境" width="60"/>
        <el-table-column prop="thread_name" label="日志线程" width="60"/>
        <el-table-column prop="logger_name" label="日志打印处" width="200"/>
        <el-table-column prop="trace_id" label="追踪码" width="120"/>
        <el-table-column prop="level" label="日志等级" width="100"/>
        <!-- 长文本列 -->
        <el-table-column label="消息内容" min-width="300">
          <template #default="{ row }">
            <!-- 用 pre 标签保留换行 -->
            <pre
                :class="['msg-content', { expanded: row._expanded }]"
                :title="row.message"
            >{{ row.message }}</pre>

            <!-- 展开 / 收起按钮 -->
            <el-button
                v-if="row.message.length > 80"
                type="text"
                size="small"
                @click="toggle(row)"
            >
              {{ row._expanded ? '收起' : '展开' }}
            </el-button>
          </template>
        </el-table-column>


      </el-table>
    </div>

    <div class="dialog-footer" style="margin:10px 0 0 0;position: fixed;right: 2%">
      <pagination @size-change="pageSizeChange" v-bind="page" @current-change="pageChange"/>
    </div>

  </div>
</template>

<script setup>
import {ref, reactive} from 'vue'
import axios from 'axios'
import zh from "element-plus/es/locale/lang/zh-cn"
import Pagination from "@/components/pagination.vue";
import {onMounted} from 'vue'

const locale = zh

const queryForm = ref({
  level: '',
  app: '',
  env: '',
  traceId: '',
  messages: []
})

const page = reactive({
  current: 1,
  size: 10,
  total: 0
})

const timeRange = ref([])


const tableData = ref([])


tableData.value = [
  // {
  //   level: 'INFO',
  //   app: 'App1',
  //   env: 'prod',
  //   traceId: 'abc123',
  //   message: '这是一个简短的日志内容',
  //   time: '2025-04-05 10:00:00'
  // },
  // {
  //   level: 'ERROR', app: 'App2', env: 'test', traceId: 'def456', message: '这是一个非常非常非常长的日志内容，\n' +
  //       '第一行\n第二行\n第三行\\n第四行\\n第五行\\n第六行\\n第七行\\n\n' +
  //       '第八行\n第九行\\n第十行\\n第十一行\\n第十二行\\n第十三行\\n第十四行\\n\n' +
  //       '第十五行用于演示展开和收起功能。第一行\\n第二行\\n第三行\\n第四行\\n\n' +
  //       '第五行\n第六行\n第七行\\n第八行\\n第九行\\n第十行。',
  //   time: '2025-04-05 10:05:00'
  // }
]
// 给每行加一个 _expanded 字段，控制展开/收起

tableData.value.forEach(r => (r._expanded = false))


const toggle = (row) => {
  row._expanded = !row._expanded
}

onMounted(() => {
  logTimeSelect(60)
  onSubmit()
})


// 添加 message 条件
const addMessage = () => {
  queryForm.value.messages.push({value: '', op: 'and'})
}

// 删除 message 条件
const removeMessage = (index) => {
  queryForm.value.messages.splice(index, 1)
}


const logTimeSelect = (minute) => {
  const start = new Date(Date.now() - minute * 60 * 1000);
  const end = new Date(new Date().setHours(23, 59, 59, 999))
  timeRange.value = [formatDate(start), formatDate(end)]
}

const formatDate = (dt) => {
  const Y = dt.getFullYear();
  const M = String(dt.getMonth() + 1).padStart(2, '0');
  const D = String(dt.getDate()).padStart(2, '0');
  const h = String(dt.getHours()).padStart(2, '0');
  const m = String(dt.getMinutes()).padStart(2, '0');
  const s = String(dt.getSeconds()).padStart(2, '0');

  return `${Y}-${M}-${D} ${h}:${m}:${s}`;

}
const pageSizeChange = async (val) => {
  page.size = val
  await onSubmit()
}
const pageChange = async (val) => {
  page.current = val
  await onSubmit()
}

// 提交查询
const onSubmit = async () => {

  const [start, end] = timeRange.value

  const payload = {
    level: queryForm.value.level,
    app: queryForm.value.app,
    env: queryForm.value.env,
    traceId: queryForm.value.traceId,
    start,
    end,
    messages: queryForm.value.messages,
    page: page.current,
    page_size: page.size
  }

  try {
    // const res = await axios.get('http://192.168.4.78:8008/log/view', {
    //   params: payload})
    const res = await axios.post('http://192.168.4.78:8008/log/view', {
 ...payload})

    const data = res.data.data;
    console.log(res.data)
    tableData.value = data.logs || []
    page.total =  data.total || 0
    console.log(tableData.value)
    // 给每行加一个 _expanded 字段，控制展开/收起
    tableData.value.forEach(r => (r._expanded = false))
  } catch (err) {
    console.error('查询失败:', err)

  }
}
</script>

<style scoped>
.message-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.query-item {
  width: 200px;
}

/* 默认最多显示 2 行，超出隐藏 */
.msg-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  overflow: hidden;
  line-height: 1.5;
}

/* 展开后取消行数限制 */
.msg-content.expanded {
  -webkit-line-clamp: unset;
}
</style>