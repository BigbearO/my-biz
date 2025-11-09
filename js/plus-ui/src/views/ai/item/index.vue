<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="所属账号" prop="accountId">
            <el-select v-model="queryParams.accountId" placeholder="请选择账号" clearable @keyup.enter="handleQuery">
              <el-option v-for="account in accountList" :key="account.id" :label="account.userId" :value="account.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="关联的闲鱼平台商品id" prop="itemId">
            <el-input v-model="queryParams.itemId" placeholder="请输入关联的闲鱼平台商品id" clearable @keyup.enter="handleQuery" />
          </el-form-item>
            <el-form-item label="商品价格" prop="price">
              <el-input v-model="queryParams.price" placeholder="请输入商品价格" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="商品描述" prop="description">
              <el-input v-model="queryParams.description" placeholder="请输入商品描述" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="商品标题" prop="title">
              <el-input v-model="queryParams.title" placeholder="请输入商品标题" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="商品分类" prop="category">
              <el-input v-model="queryParams.category" placeholder="请输入商品分类" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="商品所在地" prop="location">
              <el-input v-model="queryParams.location" placeholder="请输入商品所在地" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="商品图片URL" prop="imageUrl">
              <el-input v-model="queryParams.imageUrl" placeholder="请输入商品图片URL" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="卖家ID" prop="sellerId">
              <el-input v-model="queryParams.sellerId" placeholder="请输入卖家ID" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="商品状态" prop="condition">
              <el-input v-model="queryParams.condition" placeholder="请输入商品状态" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="发布日期" prop="publishedDate">
              <el-input v-model="queryParams.publishedDate" placeholder="请输入发布日期" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="创建时间" prop="createdAt">
              <el-date-picker clearable
                v-model="queryParams.createdAt"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择创建时间"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </transition>

    <el-card shadow="never">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['ai:item:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['ai:item:edit']">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['ai:item:remove']">删除</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['ai:item:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="itemList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="id" align="center" prop="id" v-if="true" />
        <el-table-column label="所属账号" align="center" prop="accountId" />
        <el-table-column label="关联的闲鱼平台商品id" align="center" prop="itemId" />
        <el-table-column label="商品数据，JSON格式存储" align="center" prop="data" />
        <el-table-column label="商品价格" align="center" prop="price" />
        <el-table-column label="商品描述" align="center" prop="description" />
        <el-table-column label="商品标题" align="center" prop="title" />
        <el-table-column label="商品分类" align="center" prop="category" />
        <el-table-column label="商品所在地" align="center" prop="location" />
        <el-table-column label="商品图片URL" align="center" prop="imageUrl" />
        <el-table-column label="卖家ID" align="center" prop="sellerId" />
        <el-table-column label="商品状态" align="center" prop="condition" />
        <el-table-column label="发布日期" align="center" prop="publishedDate" />
        <el-table-column label="库存状态" align="center" prop="stockStatus" />
        <el-table-column label="创建时间" align="center" prop="createdAt" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createdAt, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" align="center" prop="updatedAt" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.updatedAt, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" fixed="right" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['ai:item:edit']"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['ai:item:remove']"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
    <!-- 添加或修改商品对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="500px" append-to-body>
      <el-form ref="itemFormRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="所属账号" prop="accountId">
          <el-select v-model="form.accountId" placeholder="请选择账号" clearable>
            <el-option v-for="account in accountList" :key="account.id" :label="account.userId" :value="account.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联的闲鱼平台商品id" prop="itemId">
          <el-input v-model="form.itemId" placeholder="请输入关联的闲鱼平台商品id" />
        </el-form-item>
        <el-form-item label="商品价格" prop="price">
          <el-input v-model="form.price" placeholder="请输入商品价格" />
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
            <el-input v-model="form.description" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="商品标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入商品标题" />
        </el-form-item>
        <el-form-item label="商品分类" prop="category">
          <el-input v-model="form.category" placeholder="请输入商品分类" />
        </el-form-item>
        <el-form-item label="商品所在地" prop="location">
          <el-input v-model="form.location" placeholder="请输入商品所在地" />
        </el-form-item>
        <el-form-item label="商品图片URL" prop="imageUrl">
          <el-input v-model="form.imageUrl" placeholder="请输入商品图片URL" />
        </el-form-item>
        <el-form-item label="卖家ID" prop="sellerId">
          <el-input v-model="form.sellerId" placeholder="请输入卖家ID" />
        </el-form-item>
        <el-form-item label="商品状态" prop="condition">
          <el-input v-model="form.condition" placeholder="请输入商品状态" />
        </el-form-item>
        <el-form-item label="发布日期" prop="publishedDate">
          <el-input v-model="form.publishedDate" placeholder="请输入发布日期" />
        </el-form-item>

      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Item" lang="ts">
import { listItem, getItem, delItem, addItem, updateItem } from '@/api/ai/item';
import { listAccount } from '@/api/ai/account';
import { ItemVO, ItemQuery, ItemForm } from '@/api/ai/item/types';
import { AccountVO } from '@/api/ai/account/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const itemList = ref<ItemVO[]>([]);
const accountList = ref<AccountVO[]>([]);
const buttonLoading = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string | number>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const queryFormRef = ref<ElFormInstance>();
const itemFormRef = ref<ElFormInstance>();

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const initFormData: ItemForm = {
  id: undefined,
  accountId: undefined,
  itemId: undefined,
  data: undefined,
  price: undefined,
  description: undefined,
  title: undefined,
  category: undefined,
  location: undefined,
  imageUrl: undefined,
  sellerId: undefined,
  condition: undefined,
  publishedDate: undefined,
  stockStatus: undefined,
  createdAt: undefined,
  updatedAt: undefined
}
const data = reactive<PageData<ItemForm, ItemQuery>>({
  form: {...initFormData},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    accountId: undefined,
    itemId: undefined,
    data: undefined,
    price: undefined,
    description: undefined,
    title: undefined,
    category: undefined,
    location: undefined,
    imageUrl: undefined,
    sellerId: undefined,
    condition: undefined,
    publishedDate: undefined,
    stockStatus: undefined,
    createdAt: undefined,
    updatedAt: undefined,
    params: {
    }
  },
  rules: {
    id: [
      { required: true, message: "$comment不能为空", trigger: "blur" }
    ],
    accountId: [
      { required: true, message: "所属账号不能为空", trigger: "blur" }
    ],
    itemId: [
      { required: true, message: "关联的闲鱼平台商品id不能为空", trigger: "blur" }
    ],
    createdAt: [
      { required: true, message: "创建时间不能为空", trigger: "blur" }
    ],
    updatedAt: [
      { required: true, message: "更新时间不能为空", trigger: "blur" }
    ]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询账号列表 */
const getAccountList = async () => {
  const res = await listAccount();
  accountList.value = res.data || res.rows || res;
}

/** 查询商品列表 */
const getList = async () => {
  loading.value = true;
  const res = await listItem(queryParams.value);
  itemList.value = res.rows;
  total.value = res.total;
  loading.value = false;
}

/** 取消按钮 */
const cancel = () => {
  reset();
  dialog.visible = false;
}

/** 表单重置 */
const reset = () => {
  form.value = {...initFormData};
  itemFormRef.value?.resetFields();
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: ItemVO[]) => {
  ids.value = selection.map(item => item.id);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
const handleAdd = () => {
  reset();
  dialog.visible = true;
  dialog.title = "添加商品";
}

/** 修改按钮操作 */
const handleUpdate = async (row?: ItemVO) => {
  reset();
  const _id = row?.id || ids.value[0]
  const res = await getItem(_id);
  Object.assign(form.value, res.data);
  dialog.visible = true;
  dialog.title = "修改商品";
}

/** 提交按钮 */
const submitForm = () => {
  itemFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      buttonLoading.value = true;
      if (form.value.id) {
        await updateItem(form.value).finally(() =>  buttonLoading.value = false);
      } else {
        await addItem(form.value).finally(() =>  buttonLoading.value = false);
      }
      proxy?.$modal.msgSuccess("操作成功");
      dialog.visible = false;
      await getList();
    }
  });
}

/** 删除按钮操作 */
const handleDelete = async (row?: ItemVO) => {
  const _ids = row?.id || ids.value;
  await proxy?.$modal.confirm('是否确认删除商品编号为"' + _ids + '"的数据项？').finally(() => loading.value = false);
  await delItem(_ids);
  proxy?.$modal.msgSuccess("删除成功");
  await getList();
}

/** 导出按钮操作 */
const handleExport = () => {
  proxy?.download('ai/item/export', {
    ...queryParams.value
  }, `item_${new Date().getTime()}.xlsx`)
}

onMounted(() => {
  getAccountList();
  getList();
});
</script>
