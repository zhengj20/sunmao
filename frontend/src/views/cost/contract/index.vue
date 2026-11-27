<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-input v-model="query.kw" placeholder="合同编号/名称/承包商" clearable style="width: 220px" @keyup.enter="onSearch" />
          <el-select v-model="query.contractType" placeholder="合同类型" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="(label, key) in DICTS.contract_type" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="(label, key) in STATUS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增合同</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">合同总金额（元）</div>
          <div class="value">{{ money(summary.contractAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">变更累计（元）</div>
          <div class="value">{{ money(summary.changeAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">付款累计（元）</div>
          <div class="value">{{ money(summary.paidAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">合同余额（元）</div>
          <div class="value">{{ money(summary.remainAmount) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="contractCode" label="合同编号" width="170" show-overflow-tooltip />
        <el-table-column prop="name" label="合同名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="承包商" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ dictLabel('contract_type', row.contractType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签约金额（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.signedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="变更累计（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.changeAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="付款累计（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.paidAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="余额（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(remainOf(row)) }}</span></template>
        </el-table-column>
        <el-table-column label="签订日期" width="100" align="center">
          <template #default="{ row }">{{ fmtDate(row.signedDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="openGenerate(row)">生成合同</el-button>
            <el-button v-if="row.status === 'EFFECTIVE'" link type="success" size="small" @click="openSettle(row)">结算</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList"
        @size-change="onSearch"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="合同详情" size="62%">
      <template v-if="detail">
        <el-tabs v-model="detailTab">
          <el-tab-pane label="基础信息" name="base">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="合同编号">{{ detail.contractCode }}</el-descriptions-item>
              <el-descriptions-item label="合同名称">{{ detail.name }}</el-descriptions-item>
              <el-descriptions-item label="承包商">{{ detail.supplierName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="合同类型">{{ dictLabel('contract_type', detail.contractType) }}</el-descriptions-item>
              <el-descriptions-item label="签约金额（元）"><span class="num">{{ money(detail.signedAmount) }}</span></el-descriptions-item>
              <el-descriptions-item label="变更累计（元）"><span class="num">{{ money(detail.changeAmount) }}</span></el-descriptions-item>
              <el-descriptions-item label="付款累计（元）"><span class="num">{{ money(detail.paidAmount) }}</span></el-descriptions-item>
              <el-descriptions-item label="余额（元）"><span class="num">{{ money(remainOf(detail)) }}</span></el-descriptions-item>
              <el-descriptions-item label="结算金额（元）"><span class="num">{{ money(detail.settledAmount) }}</span></el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="statusColor(detail.status)" size="small">{{ STATUS[detail.status] || detail.status }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="质保金比例">{{ detail.retentionRate != null ? detail.retentionRate + '%' : '-' }}</el-descriptions-item>
              <el-descriptions-item label="已释放质保金（元）"><span class="num">{{ money(detail.retentionReleased) }}</span></el-descriptions-item>
              <el-descriptions-item label="签订日期">{{ fmtDate(detail.signedDate) }}</el-descriptions-item>
              <el-descriptions-item label="施工起止">
                {{ fmtDate(detail.startDate) }} ~ {{ fmtDate(detail.endDate) }}
              </el-descriptions-item>
              <el-descriptions-item label="来源">{{ detail.source === 'AWARD' ? '招采定标生成' : '手工登记' }}</el-descriptions-item>
              <el-descriptions-item label="承包范围">{{ detail.scope || '-' }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="changeRate && changeRate.warn" class="change-rate-warn">
              <el-alert type="warning" :closable="false" show-icon>
                <template #title>
                  变更率预警：当前变更率 <b>{{ changeRate.changeRate }}%</b>，已超过预警阈值 <b>{{ changeRate.warnRate }}%</b>
                  （确认变更累计 {{ money(changeRate.confirmedChange) }} 元 / 签约金额 {{ money(changeRate.signedAmount) }} 元）
                </template>
              </el-alert>
            </div>
            <div v-if="detail.status === 'EFFECTIVE'" class="retention-section">
              <el-divider content-position="left">合同结算</el-divider>
              <div class="settle-hint">结算金额 = 签约金额 + 确认变更 − 扣款。确认后合同转为「已结算」，可释放质保金。</div>
              <el-button type="primary" size="small" style="margin-top:8px" @click="openSettle(detail)">发起结算</el-button>
            </div>
            <div v-if="detail.retentionRate > 0 && (detail.status === 'SETTLED' || detail.status === 'EFFECTIVE')" class="retention-section">
              <el-divider content-position="left">质保金信息</el-divider>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="质保金比例">{{ detail.retentionRate }}%</el-descriptions-item>
                <el-descriptions-item label="质保金总额（元）">
                  <span class="num">{{ money(retentionAmount) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="已释放（元）">
                  <span class="num">{{ money(detail.retentionReleased) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="待释放（元）">
                  <span class="num">{{ money(retentionRemain) }}</span>
                </el-descriptions-item>
              </el-descriptions>
              <el-button v-if="retentionRemain > 0" type="warning" size="small" style="margin-top: 8px" @click="openReleaseRetention">释放质保金</el-button>
            </div>
          </el-tab-pane>
          <el-tab-pane label="科目分摊" name="alloc">
            <el-table :data="detail.allocs || []" border size="small">
              <el-table-column prop="accountName" label="科目名称" min-width="160" show-overflow-tooltip />
              <el-table-column label="分摊金额（元）" width="150" align="right">
                <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="付款计划" name="plan">
            <div style="margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center">
              <span class="hint">合同付款计划是资金计划成本线的数据来源，也是成本付款 B3 月度预算管控的依据</span>
              <el-button type="primary" size="small" @click="openPlanForm">+ 新增付款计划</el-button>
            </div>
            <el-table :data="paymentPlans" border size="small">
              <el-table-column prop="termNo" label="期数" width="60" align="center" />
              <el-table-column prop="termDesc" label="付款条件" min-width="160" show-overflow-tooltip />
              <el-table-column label="计划金额（元）" width="130" align="right">
                <template #default="{ row }"><span class="num">{{ money(row.planAmount) }}</span></template>
              </el-table-column>
              <el-table-column label="已付（元）" width="120" align="right">
                <template #default="{ row }"><span class="num">{{ money(row.paidAmount) }}</span></template>
              </el-table-column>
              <el-table-column label="计划日期" width="100" align="center">
                <template #default="{ row }">{{ fmtDate(row.planDate) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" align="center">
                <template #default="{ row }">
                  <el-button link size="small" type="primary" @click="openPlanForm(row)">编辑</el-button>
                  <el-button link size="small" type="danger" @click="onDeletePlan(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`变更记录（${changes.length}）`" name="change">
            <el-table :data="changes" border size="small">
              <el-table-column prop="changeCode" label="变更编号" width="160" show-overflow-tooltip />
              <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">{{ dictLabel('change_type', row.changeType) }}</template>
              </el-table-column>
              <el-table-column label="确认金额（元）" width="120" align="right">
                <template #default="{ row }"><span class="num">{{ money(row.confirmedAmount) }}</span></template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusColor(row.status)" size="small">{{ CHANGE_STATUS[row.status] || row.status }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`付款记录（${payments.length}）`" name="payment">
            <el-table :data="payments" border size="small">
              <el-table-column prop="paymentCode" label="付款编号" width="160" show-overflow-tooltip />
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">{{ dictLabel('payment_type', row.paymentType) }}</template>
              </el-table-column>
              <el-table-column label="申请金额（元）" width="120" align="right">
                <template #default="{ row }"><span class="num">{{ money(row.applyAmount) }}</span></template>
              </el-table-column>
              <el-table-column label="实付金额（元）" width="120" align="right">
                <template #default="{ row }"><span class="num">{{ money(row.paidAmount) }}</span></template>
              </el-table-column>
              <el-table-column label="付款日期" width="100" align="center">
                <template #default="{ row }">{{ fmtDate(row.paidDate) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusColor(row.status)" size="small">{{ PAY_STATUS[row.status] || row.status }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑合同' : '新增合同'" width="640px" top="5vh">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="合同名称" required>
              <el-input v-model="form.name" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同类型" required>
              <el-select v-model="form.contractType" style="width: 100%">
                <el-option v-for="(label, key) in DICTS.contract_type" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="承包商" required>
              <el-select v-model="form.supplierId" filterable style="width: 100%" @change="onSupplierChange">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签约金额" required>
              <el-input-number v-model="form.signedAmount" :controls="false" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联合约规划">
              <el-select v-model="form.planId" clearable placeholder="选择规划" style="width: 100%" @change="form.planItemId = null">
                <el-option v-for="p in plans" :key="p.id" :label="p.planCode + '（' + (p.name || '') + '）'" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规划科目">
              <el-select v-model="form.planItemId" clearable :disabled="!form.planId" style="width: 100%" placeholder="选择规划明细科目">
                <el-option v-for="i in planItemOptions" :key="i.id" :label="i.accountName" :value="i.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="签订日期">
              <el-date-picker v-model="form.signedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开工日期">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="竣工日期">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="质保金比例">
              <el-input-number v-model="form.retentionRate" :controls="false" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="承包范围">
              <el-input v-model="form.scope" type="textarea" :rows="2" maxlength="300" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="科目分摊">
              <div class="alloc-editor">
                <div v-for="(a, idx) in form.allocs" :key="idx" class="alloc-row">
                  <el-select v-model="a.accountId" filterable placeholder="选择科目" style="width: 220px" @change="onAllocAccount(a)">
                    <el-option v-for="acc in accountLeafs" :key="acc.id" :label="acc.code + ' ' + acc.name" :value="acc.id" />
                  </el-select>
                  <el-input-number v-model="a.amount" :controls="false" :min="0" placeholder="分摊金额" style="width: 160px" />
                  <el-button link type="danger" @click="form.allocs.splice(idx, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.allocs.push({ accountId: null, accountName: '', amount: null })">+ 添加分摊</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 释放质保金 -->
    <el-dialog v-model="retentionVisible" title="释放质保金" width="440px">
      <el-alert type="info" :closable="false" title="质保期满后可释放质保金，释放后将从合同质保金中扣除" style="margin-bottom: 14px" />
      <el-form :model="retentionForm" label-width="100px">
        <el-form-item label="可释放金额">
          <span class="num">{{ money(retentionRemain) }} 元</span>
        </el-form-item>
        <el-form-item label="本次释放" required>
          <el-input-number v-model="retentionForm.releaseAmount" :controls="false" :min="0" :max="retentionRemain" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="retentionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onReleaseRetention">确定</el-button>
      </template>
    </el-dialog>

    <!-- 合同结算 -->
    <el-dialog v-model="settleVisible" title="合同结算" width="480px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px">
        结算金额须等于「签约金额 + 确认变更 − 扣款」= <span class="num">{{ money(settleForm.amount) }}</span> 元，否则后端将驳回。
      </el-alert>
      <el-descriptions :column="2" border size="small" style="margin-bottom:14px">
        <el-descriptions-item label="签约金额"><span class="num">{{ money(settleForm.signed) }}</span></el-descriptions-item>
        <el-descriptions-item label="确认变更"><span class="num" :style="settleForm.change > 0 ? 'color:#c45656' : ''">{{ money(settleForm.change) }}</span></el-descriptions-item>
      </el-descriptions>
      <el-form label-width="100px">
        <el-form-item label="结算金额" required>
          <el-input-number v-model="settleForm.amount" :controls="false" :precision="2" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSettle">确认结算</el-button>
      </template>
    </el-dialog>

    <!-- 付款计划 -->
    <el-dialog v-model="planVisible" :title="planForm.id ? '编辑付款计划' : '新增付款计划'" width="480px">
      <el-form :model="planForm" label-width="100px">
        <el-form-item label="期数" required>
          <el-input-number v-model="planForm.termNo" :controls="false" :min="1" :step="1" style="width: 120px" />
        </el-form-item>
        <el-form-item label="付款条件" required>
          <el-input v-model="planForm.termDesc" maxlength="100" placeholder="如：开工后30天内付至合同额的30%" />
        </el-form-item>
        <el-form-item label="计划金额（元）" required>
          <el-input-number v-model="planForm.planAmount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划日期" required>
          <el-date-picker v-model="planForm.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="选择计划付款日期" />
        </el-form-item>
        <el-form-item v-if="planForm.id" label="已付金额">
          <span class="num">{{ money(planForm.paidAmount) }} 元（由付款单自动回写，不可修改）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSavePlan">确定</el-button>
      </template>
    </el-dialog>

    <!-- 生成合同 -->
    <el-dialog v-model="genVisible" title="根据模板生成合同" width="70%" top="5vh" class="gen-dialog">
      <div v-if="genRow">
        <el-descriptions :column="3" border size="small" style="margin-bottom: 14px">
          <el-descriptions-item label="合同编号">{{ genRow.contractCode }}</el-descriptions-item>
          <el-descriptions-item label="合同名称">{{ genRow.name }}</el-descriptions-item>
          <el-descriptions-item label="承包商">{{ genRow.supplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签约金额（元）"><span class="num">{{ money(genRow.signedAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="签订日期">{{ fmtDate(genRow.signedDate) }}</el-descriptions-item>
          <el-descriptions-item label="质保金比例">{{ genRow.retentionRate != null ? genRow.retentionRate + '%' : '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-form label-width="90px">
          <el-form-item label="选择模板" required>
            <el-select v-model="genForm.templateId" filterable placeholder="选择一个合同模板" style="width: 100%">
              <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id">
                <span>{{ t.name }}</span>
                <span v-if="t.contractType" style="float:right; color:#a0a4ab; font-size:12px">{{ dictLabel('contract_type', t.contractType) }}</span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-form>

        <el-divider style="margin: 8px 0 14px" />
        <div class="gen-result-head">
          <span class="gen-result-title">生成结果</span>
          <el-button v-if="genResult" size="small" type="primary" plain @click="downloadGenerated">下载 .docx</el-button>
        </div>
        <div v-if="genLoading" class="gen-state">正在用合同数据填充模板占位符，生成中…</div>
        <div v-else-if="genResult" class="gen-preview">
          <DocPreview :file-url="genResult.fileUrl" />
        </div>
        <el-empty v-else description="选择模板并点击「生成合同」后，在此预览填充后的合同" :image-size="80" />
      </div>
      <template #footer>
        <el-button @click="genVisible = false">关闭</el-button>
        <el-button type="primary" :loading="genLoading" :disabled="!genForm.templateId" @click="onGenerate">生成合同</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate, statusColor, dictLabel, DICTS } from '../../../utils/format'
import {
  fetchProjectOptions, fetchAccountTree, fetchSupplierOptions,
  fetchContracts, fetchContractSummary, fetchContractDetail,
  fetchContractChanges, fetchContractPayments, fetchContractPaymentPlans, fetchContractChangeRate,
  addContractPaymentPlan, updateContractPaymentPlan, deleteContractPaymentPlan,
  fetchPlans, fetchPlanDetail, createContract, updateContract, deleteContract, releaseRetention, settleContract,
  fetchTemplates, generateContract, fileUrl
} from '../../../api/cost'
import DocPreview from '../../../components/DocPreview.vue'

const STATUS = { DRAFT: '草稿', APPROVING: '审批中', EFFECTIVE: '已生效', SETTLED: '已结算', CLOSED: '已关闭' }
const CHANGE_STATUS = { ESTIMATING: '估价中', ESTIMATE_APPROVING: '估价审批中', CONFIRMING: '待确认', APPROVING: '确认审批中', CONFIRMED: '已确认', REJECTED: '已驳回' }
const PAY_STATUS = { DRAFT: '草稿', APPROVING: '审批中', APPROVED: '审批通过', PAID: '已付款', REJECTED: '已驳回' }

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const suppliers = ref([])
const plans = ref([])
const planItemsMap = ref({})
const accountLeafs = ref([])
const rows = ref([])
const total = ref(0)
const summary = ref({ contractAmount: null, changeAmount: null, paidAmount: null, remainAmount: null })
const query = reactive({ projectId: null, kw: '', contractType: null, status: null, current: 1, size: 20 })

const detailVisible = ref(false)
const detailTab = ref('base')
const detail = ref(null)
const changes = ref([])
const payments = ref([])
const paymentPlans = ref([])
const changeRate = ref(null)

const planVisible = ref(false)
const planForm = reactive({ id: null, termNo: 1, termDesc: '', planAmount: null, planDate: null, paidAmount: 0 })

const formVisible = ref(false)
const form = reactive({
  id: null, name: '', contractType: 'GC', supplierId: null, supplierName: '',
  signedAmount: null, planId: null, planItemId: null,
  signedDate: null, startDate: null, endDate: null, retentionRate: 3,
  scope: '', allocs: []
})

const retentionVisible = ref(false)
const retentionForm = reactive({ releaseAmount: null })
const settleVisible = ref(false)
const settleForm = reactive({ id: null, amount: null, signed: 0, change: 0 })

// 生成合同
const genVisible = ref(false)
const genRow = ref(null)
const genForm = reactive({ templateId: null })
const genLoading = ref(false)
const genResult = ref(null)
const templates = ref([])

const planItemOptions = computed(() => planItemsMap.value[form.planId] || [])
const remainOf = row => (Number(row.signedAmount) || 0) + (Number(row.changeAmount) || 0) - (Number(row.paidAmount) || 0)

const retentionAmount = computed(() => {
  if (!detail.value) return 0
  return (Number(detail.value.signedAmount) || 0) * (Number(detail.value.retentionRate) || 0) / 100
})
const retentionRemain = computed(() => {
  return retentionAmount.value - (Number(detail.value?.retentionReleased) || 0)
})

const flattenLeafs = (list, out = []) => {
  ;(list || []).forEach(n => {
    if (n.children?.length) flattenLeafs(n.children, out)
    else out.push(n)
  })
  return out
}

const loadList = async () => {
  loading.value = true
  try {
    const [page, sum] = await Promise.all([
      fetchContracts(query),
      fetchContractSummary({ projectId: query.projectId })
    ])
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
    summary.value = sum || summary.value
  } finally {
    loading.value = false
  }
}

const onSearch = () => { query.current = 1; loadList() }
const onReset = () => {
  query.kw = ''
  query.contractType = null
  query.status = null
  onSearch()
}

const openDetail = async row => {
  detailTab.value = 'base'
  detail.value = row
  changes.value = []
  payments.value = []
  paymentPlans.value = []
  changeRate.value = null
  detailVisible.value = true
  const [d, cs, ps, pps, cr] = await Promise.all([
    fetchContractDetail(row.id),
    fetchContractChanges(row.id),
    fetchContractPayments(row.id),
    fetchContractPaymentPlans(row.id),
    fetchContractChangeRate(row.id).catch(() => null)
  ])
  detail.value = { ...row, ...d }
  changes.value = cs || []
  payments.value = ps || []
  paymentPlans.value = pps || []
  changeRate.value = cr
}

const openForm = async row => {
  if (!suppliers.value.length) suppliers.value = (await fetchSupplierOptions()) || []
  if (!plans.value.length && query.projectId) {
    const page = await fetchPlans({ projectId: query.projectId, current: 1, size: 100 })
    plans.value = page?.records || []
  }
  if (!accountLeafs.value.length) accountLeafs.value = flattenLeafs(await fetchAccountTree({ projectId: query.projectId }))
  if (row) {
    Object.assign(form, {
      id: row.id, name: row.name, contractType: row.contractType || 'GC',
      supplierId: row.supplierId, supplierName: row.supplierName || '',
      signedAmount: row.signedAmount, planId: row.planId, planItemId: row.planItemId,
      signedDate: row.signedDate, startDate: row.startDate, endDate: row.endDate,
      retentionRate: row.retentionRate ?? 3, scope: row.scope || '',
      allocs: (row.allocs || []).map(a => ({ ...a }))
    })
    if (form.planId && !planItemsMap.value[form.planId]) await loadPlanItems(form.planId)
  } else {
    Object.assign(form, {
      id: null, name: '', contractType: 'GC', supplierId: null, supplierName: '',
      signedAmount: null, planId: null, planItemId: null,
      signedDate: null, startDate: null, endDate: null, retentionRate: 3,
      scope: '', allocs: []
    })
  }
  formVisible.value = true
}

const loadPlanItems = async planId => {
  const detail = await fetchPlanDetail(planId)
  planItemsMap.value[planId] = (detail?.items || []).map(i => ({ id: i.id, accountName: i.accountCode + ' ' + i.accountName }))
}

const onSupplierChange = id => {
  form.supplierName = suppliers.value.find(s => s.id === id)?.name || ''
}
const onPlanChange = async planId => {
  form.planItemId = null
  if (planId && !planItemsMap.value[planId]) await loadPlanItems(planId)
}
const onAllocAccount = a => {
  const acc = accountLeafs.value.find(x => x.id === a.accountId)
  a.accountName = acc?.name || ''
}

const onSave = async () => {
  if (!form.name) return ElMessage.warning('请填写合同名称')
  if (!form.supplierId) return ElMessage.warning('请选择承包商')
  if (form.signedAmount == null) return ElMessage.warning('请填写签约金额')
  saving.value = true
  try {
    const payload = {
      ...form, projectId: query.projectId,
      allocs: form.allocs.filter(a => a.accountId && a.amount != null)
    }
    if (form.id) {
      await updateContract(payload)
      ElMessage.success('合同已更新')
    } else {
      await createContract(payload)
      ElMessage.success('合同已创建')
    }
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除合同【${row.contractCode}】？仅草稿状态可删除`, '提示', { type: 'warning' })
  await deleteContract(row.id)
  ElMessage.success('已删除')
  await loadList()
}

const openReleaseRetention = () => {
  retentionForm.releaseAmount = null
  retentionVisible.value = true
}

const onReleaseRetention = async () => {
  if (retentionForm.releaseAmount == null || retentionForm.releaseAmount <= 0) return ElMessage.warning('请填写释放金额')
  saving.value = true
  try {
    await releaseRetention(detail.value.id, { releaseAmount: retentionForm.releaseAmount })
    ElMessage.success('质保金释放成功')
    retentionVisible.value = false
    await openDetail(detail.value)
  } finally {
    saving.value = false
  }
}

const openPlanForm = row => {
  if (row) {
    Object.assign(planForm, { id: row.id, termNo: row.termNo, termDesc: row.termDesc || '', planAmount: row.planAmount, planDate: row.planDate, paidAmount: row.paidAmount ?? 0 })
  } else {
    const nextNo = paymentPlans.value.length ? Math.max(...paymentPlans.value.map(p => Number(p.termNo) || 0)) + 1 : 1
    Object.assign(planForm, { id: null, termNo: nextNo, termDesc: '', planAmount: null, planDate: null, paidAmount: 0 })
  }
  planVisible.value = true
}

const onSavePlan = async () => {
  if (planForm.termDesc == null || !String(planForm.termDesc).trim()) return ElMessage.warning('请填写付款条件')
  if (planForm.planAmount == null || planForm.planAmount <= 0) return ElMessage.warning('请填写计划金额')
  if (!planForm.planDate) return ElMessage.warning('请选择计划日期')
  saving.value = true
  try {
    if (planForm.id) {
      await updateContractPaymentPlan({ contractId: detail.value.id, id: planForm.id, termNo: planForm.termNo, termDesc: planForm.termDesc, planAmount: planForm.planAmount, planDate: planForm.planDate })
      ElMessage.success('付款计划已更新')
    } else {
      await addContractPaymentPlan(detail.value.id, { termNo: planForm.termNo, termDesc: planForm.termDesc, planAmount: planForm.planAmount, planDate: planForm.planDate })
      ElMessage.success('付款计划已新增')
    }
    planVisible.value = false
    await openDetail(detail.value)
  } finally {
    saving.value = false
  }
}

const onDeletePlan = async row => {
  const paid = Number(row.paidAmount) || 0
  await ElMessageBox.confirm(paid > 0
    ? `该期已付款 ${money(row.paidAmount)} 元，删除后资金计划与付款统计将不一致，确认删除？`
    : `确认删除第 ${row.termNo} 期付款计划？`, '提示', { type: 'warning' })
  await deleteContractPaymentPlan(detail.value.id, row.id)
  ElMessage.success('已删除')
  await openDetail(detail.value)
}

const openSettle = async row => {
  const signed = Number(row.signedAmount) || 0
  let list = null
  if (row.id === detail.value?.id) list = changes.value
  if (!list) list = (await fetchContractChanges(row.id).catch(() => [])) || []
  let confirmedNet = 0
  for (const c of list) if (c.status === 'CONFIRMED') confirmedNet += (Number(c.confirmedAmount) || 0) - (Number(c.deductAmount) || 0)
  settleForm.id = row.id
  settleForm.signed = signed
  settleForm.change = confirmedNet
  settleForm.amount = signed + confirmedNet
  settleVisible.value = true
}

const onSettle = async () => {
  if (settleForm.amount == null) return ElMessage.warning('请填写结算金额')
  saving.value = true
  try {
    await settleContract(settleForm.id, { settledAmount: settleForm.amount })
    ElMessage.success('结算完成，合同已转为「已结算」')
    settleVisible.value = false
    await loadList()
    if (detailVisible.value) await openDetail(detail.value)
  } finally {
    saving.value = false
  }
}

const openGenerate = async row => {
  genRow.value = row
  genForm.templateId = null
  genResult.value = null
  genVisible.value = true
  if (!templates.value.length) {
    const page = await fetchTemplates({ current: 1, size: 100 }).catch(() => null)
    templates.value = page?.records || []
  }
  if (templates.value.length) genForm.templateId = templates.value[0].id
}

const onGenerate = async () => {
  if (!genForm.templateId) return ElMessage.warning('请选择合同模板')
  genLoading.value = true
  genResult.value = null
  try {
    const r = await generateContract(genForm.templateId, genRow.value.id)
    genResult.value = r
    ElMessage.success('合同已生成，可预览或下载')
  } finally {
    genLoading.value = false
  }
}

const downloadGenerated = () => {
  if (!genResult.value) return
  const a = document.createElement('a')
  a.href = genResult.value.fileUrl
  a.download = genResult.value.fileName || '合同.docx'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

onMounted(async () => {
  projects.value = await fetchProjectOptions()
  if (projects.value.length) query.projectId = projects.value[0].id
  await loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
.alloc-editor { width: 100%; }
.alloc-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.change-rate-warn { margin-top: 12px; }
.retention-section { margin-top: 12px; }
.settle-hint { font-size: 12.5px; color: #6b7280; line-height: 1.6; }
.gen-result-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.gen-result-title { font-weight: 600; color: #303133; }
.gen-state { text-align: center; color: #909399; padding: 40px 0; }
.gen-preview { height: 52vh; border: 1px solid #ebeef5; border-radius: 4px; overflow: hidden; }
</style>
