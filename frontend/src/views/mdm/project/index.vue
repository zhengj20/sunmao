<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键字">
            <el-input v-model="query.kw" placeholder="项目名称/编号" clearable style="width: 200px" :prefix-icon="Search" @clear="load" />
          </el-form-item>
          <el-form-item label="项目状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px" @change="load">
              <el-option v-for="(label, value) in PRJ_STATUS" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增项目</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border style="width: 100%">
        <el-table-column prop="projectCode" label="项目编号" width="110" show-overflow-tooltip />
        <el-table-column prop="name" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="所属区域" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ orgName(row.orgId) }}</template>
        </el-table-column>
        <el-table-column prop="city" label="城市" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.city || '-' }}</template>
        </el-table-column>
        <el-table-column prop="cooperationMode" label="合作模式" width="90" align="center">
          <template #default="{ row }">{{ row.cooperationMode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="landAcquiredDate" label="拿地日期" width="105" align="center">
          <template #default="{ row }">{{ row.landAcquiredDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="占地面积(㎡)" width="120" align="right" class="num">
          <template #default="{ row }">{{ money(row.landArea) }}</template>
        </el-table-column>
        <el-table-column label="可售面积(㎡)" width="120" align="right" class="num">
          <template #default="{ row }">{{ money(row.saleableArea) }}</template>
        </el-table-column>
        <el-table-column label="土地款(万元)" width="120" align="right" class="num">
          <template #default="{ row }">{{ money(row.landCost ? row.landCost / 10000 : null) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" class="status-tag">{{ PRJ_STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <!-- 新增/编辑项目 -->
    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑项目' : '新增项目'" width="720px" destroy-on-close top="4vh">
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="项目编号" v-if="dlg.form.id">
              <el-input v-model="dlg.form.projectCode" disabled />
            </el-form-item>
            <el-form-item label="项目名称" prop="name">
              <el-input v-model="dlg.form.name" placeholder="请输入项目名称" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属区域" prop="orgId">
              <el-tree-select
                v-model="dlg.form.orgId"
                :data="orgOptions"
                node-key="id"
                :props="{ label: 'name', children: 'children' }"
                check-strictly
                clearable
                default-expand-all
                placeholder="请选择所属组织（区域）"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市" prop="city">
              <el-input v-model="dlg.form.city" placeholder="如：深圳" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合作模式" prop="cooperationMode">
              <el-select v-model="dlg.form.cooperationMode" placeholder="请选择合作模式" clearable style="width: 100%">
                <el-option v-for="m in COOP_MODES" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="项目地址" prop="address">
              <el-input v-model="dlg.form.address" placeholder="请输入项目地址" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合作方" prop="partner">
              <el-input v-model="dlg.form.partner" placeholder="合作/代建模式下填写" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="拿地日期" prop="landAcquiredDate">
              <el-date-picker v-model="dlg.form.landAcquiredDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="占地面积(㎡)" prop="landArea">
              <el-input-number v-model="dlg.form.landArea" :min="0" :precision="2" :controls="false" placeholder="0.00" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计容面积(㎡)" prop="farArea">
              <el-input-number v-model="dlg.form.farArea" :min="0" :precision="2" :controls="false" placeholder="0.00" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可售面积(㎡)" prop="saleableArea">
              <el-input-number v-model="dlg.form.saleableArea" :min="0" :precision="2" :controls="false" placeholder="0.00" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="容积率" prop="plotRatio">
              <el-input-number v-model="dlg.form.plotRatio" :min="0" :precision="3" :controls="false" placeholder="0.000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="土地款(元)" prop="landCost">
              <el-input-number v-model="dlg.form.landCost" :min="0" :precision="2" :controls="false" placeholder="0.00" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="dlg.form.id">
            <el-form-item label="项目状态" prop="status">
              <el-select v-model="dlg.form.status" style="width: 100%">
                <el-option v-for="(label, value) in PRJ_STATUS" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="dlg.form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 项目详情抽屉 -->
    <el-drawer v-model="drawer.visible" size="78%" :title="`项目详情 - ${drawer.project?.name || ''}`" destroy-on-close>
      <template #header>
        <div class="drawer-header">
          <span class="title">项目详情 - {{ drawer.project?.name }}</span>
          <el-tag :type="statusColor(drawer.project?.status)">{{ PRJ_STATUS[drawer.project?.status] || '-' }}</el-tag>
          <el-button
            v-if="nextStatus(drawer.project?.status)"
            size="small" type="warning" plain style="margin-left: 12px"
            @click="onChangeStatus(drawer.project)"
          >流转为「{{ PRJ_STATUS[nextStatus(drawer.project?.status)] }}」</el-button>
        </div>
      </template>

      <el-descriptions :column="3" border size="small" class="info-block">
        <el-descriptions-item label="项目编号">{{ drawer.project?.projectCode }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ drawer.project?.name }}</el-descriptions-item>
        <el-descriptions-item label="所属区域">{{ orgName(drawer.project?.orgId) }}</el-descriptions-item>
        <el-descriptions-item label="城市">{{ drawer.project?.city || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目地址" :span="2">{{ drawer.project?.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合作模式">{{ drawer.project?.cooperationMode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合作方" :span="2">{{ drawer.project?.partner || '-' }}</el-descriptions-item>
        <el-descriptions-item label="拿地日期">{{ drawer.project?.landAcquiredDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="占地面积(㎡)"><span class="num">{{ money(drawer.project?.landArea) }}</span></el-descriptions-item>
        <el-descriptions-item label="计容面积(㎡)"><span class="num">{{ money(drawer.project?.farArea) }}</span></el-descriptions-item>
        <el-descriptions-item label="可售面积(㎡)"><span class="num">{{ money(drawer.project?.saleableArea) }}</span></el-descriptions-item>
        <el-descriptions-item label="容积率"><span class="num">{{ drawer.project?.plotRatio ?? '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="土地款(元)"><span class="num">{{ money(drawer.project?.landCost) }}</span></el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ drawer.project?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="drawer.tab" class="detail-tabs">
        <!-- 分期 -->
        <el-tab-pane label="分期管理" name="phases">
          <div class="tab-toolbar">
            <span class="tab-title">分期列表</span>
            <el-button type="primary" size="small" :icon="Plus" @click="openPhaseDialog()">新增分期</el-button>
          </div>
          <el-table :data="phases" v-loading="drawer.loading" border size="small">
            <el-table-column prop="phaseCode" label="分期编码" width="110" show-overflow-tooltip />
            <el-table-column prop="name" label="分期名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="计容面积(㎡)" width="120" align="right" class="num">
              <template #default="{ row }">{{ money(row.farArea) }}</template>
            </el-table-column>
            <el-table-column label="可售面积(㎡)" width="120" align="right" class="num">
              <template #default="{ row }">{{ money(row.saleableArea) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="statusColor(row.status)" size="small">{{ PRJ_STATUS[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openPhaseDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onPhaseDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 业态 -->
        <el-tab-pane label="业态管理" name="productTypes">
          <div class="tab-toolbar">
            <span class="tab-title">业态列表</span>
            <el-button type="primary" size="small" :icon="Plus" @click="openPtDialog()">新增业态</el-button>
          </div>
          <el-table :data="productTypes" v-loading="drawer.loading" border size="small">
            <el-table-column prop="name" label="业态名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="category" label="业态分类" width="110" align="center">
              <template #default="{ row }">{{ row.category || '-' }}</template>
            </el-table-column>
            <el-table-column label="是否可售" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.saleable === 1 ? 'success' : 'info'" size="small">{{ row.saleable === 1 ? '可售' : '不可售' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="计容面积(㎡)" width="120" align="right" class="num">
              <template #default="{ row }">{{ money(row.farArea) }}</template>
            </el-table-column>
            <el-table-column label="可售面积(㎡)" width="120" align="right" class="num">
              <template #default="{ row }">{{ money(row.saleableArea) }}</template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="70" align="center" class="num" />
            <el-table-column label="操作" width="130" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openPtDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onPtDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 楼栋 -->
        <el-tab-pane label="楼栋管理" name="buildings">
          <div class="tab-toolbar">
            <span class="tab-title">楼栋列表</span>
            <el-button type="primary" size="small" :icon="Plus" @click="openBuildingDialog()">新增楼栋</el-button>
          </div>
          <el-table :data="buildings" v-loading="drawer.loading" border size="small">
            <el-table-column prop="buildingCode" label="栋号" width="90" align="center" show-overflow-tooltip />
            <el-table-column prop="name" label="楼栋名称" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.name || '-' }}</template>
            </el-table-column>
            <el-table-column label="所属分期" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ phaseName(row.phaseId) }}</template>
            </el-table-column>
            <el-table-column prop="floors" label="地上层数" width="90" align="center" class="num" />
            <el-table-column prop="undergroundFloors" label="地下层数" width="90" align="center" class="num" />
            <el-table-column prop="units" label="单元数" width="80" align="center" class="num" />
            <el-table-column label="总面积(㎡)" width="110" align="right" class="num">
              <template #default="{ row }">{{ money(row.totalArea) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="BLD_STATUS_TAG[row.status] || 'info'" size="small">{{ BLD_STATUS[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openBuildingDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onBuildingDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 房间 -->
        <el-tab-pane label="房间管理" name="rooms">
          <div class="tab-toolbar">
            <el-select v-model="roomQuery.buildingId" placeholder="选择楼栋" clearable style="width: 220px" @change="loadRooms">
              <el-option v-for="b in buildings" :key="b.id" :label="`${b.buildingCode} ${b.name || ''}`" :value="b.id" />
            </el-select>
            <el-select v-model="roomQuery.saleStatus" placeholder="销控状态" clearable style="width: 120px" @change="loadRooms">
              <el-option v-for="(label, value) in SALE_STATUS" :key="value" :label="label" :value="value" />
            </el-select>
            <el-button :icon="Search" @click="loadRooms">查询</el-button>
            <el-button type="primary" :icon="MagicStick" :disabled="!roomQuery.buildingId" @click="gen.visible = true">批量生成房间</el-button>
          </div>
          <el-table :data="rooms" v-loading="roomLoading" border size="small">
            <el-table-column prop="roomCode" label="房间编号" width="160" show-overflow-tooltip />
            <el-table-column prop="floorNo" label="楼层" width="70" align="center" class="num" />
            <el-table-column prop="unitNo" label="单元" width="70" align="center" class="num" />
            <el-table-column prop="roomNo" label="户室号" width="90" align="center" show-overflow-tooltip />
            <el-table-column prop="layout" label="户型" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.layout || '-' }}</template>
            </el-table-column>
            <el-table-column prop="orientation" label="朝向" width="80" align="center">
              <template #default="{ row }">{{ row.orientation || '-' }}</template>
            </el-table-column>
            <el-table-column label="建筑面积(㎡)" width="110" align="right" class="num">
              <template #default="{ row }">{{ money(row.area) }}</template>
            </el-table-column>
            <el-table-column label="套内面积(㎡)" width="110" align="right" class="num">
              <template #default="{ row }">{{ money(row.innerArea) }}</template>
            </el-table-column>
            <el-table-column label="可售" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.saleable === 1 ? 'success' : 'info'" size="small">{{ row.saleable === 1 ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="销控状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="SALE_STATUS_TAG[row.saleStatus] || 'info'" size="small">{{ SALE_STATUS[row.saleStatus] || row.saleStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openRoomDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="onRoomDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-bar" style="border: none; padding-left: 0;">
            <el-pagination
              v-model:current-page="roomPager.current"
              v-model:page-size="roomPager.size"
              :total="roomPager.total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadRooms"
              @size-change="loadRooms"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 分期弹窗 -->
    <el-dialog v-model="phaseDlg.visible" :title="phaseDlg.form.id ? '编辑分期' : '新增分期'" width="520px" destroy-on-close>
      <el-form ref="phaseFormRef" :model="phaseDlg.form" :rules="phaseRules" label-width="110px">
        <el-form-item label="分期编码" prop="phaseCode">
          <el-input v-model="phaseDlg.form.phaseCode" placeholder="如：P1" maxlength="32" />
        </el-form-item>
        <el-form-item label="分期名称" prop="name">
          <el-input v-model="phaseDlg.form.name" placeholder="如：一期" maxlength="128" />
        </el-form-item>
        <el-form-item label="计容面积(㎡)" prop="farArea">
          <el-input-number v-model="phaseDlg.form.farArea" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="可售面积(㎡)" prop="saleableArea">
          <el-input-number v-model="phaseDlg.form.saleableArea" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="phaseDlg.form.status" style="width: 100%">
            <el-option v-for="(label, value) in PRJ_STATUS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="phaseDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="phaseDlg.saving" @click="onPhaseSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 业态弹窗 -->
    <el-dialog v-model="ptDlg.visible" :title="ptDlg.form.id ? '编辑业态' : '新增业态'" width="520px" destroy-on-close>
      <el-form ref="ptFormRef" :model="ptDlg.form" :rules="ptRules" label-width="110px">
        <el-form-item label="业态名称" prop="name">
          <el-input v-model="ptDlg.form.name" placeholder="如：高层住宅" maxlength="64" />
        </el-form-item>
        <el-form-item label="业态分类" prop="category">
          <el-select v-model="ptDlg.form.category" placeholder="请选择分类" clearable style="width: 100%">
            <el-option v-for="c in PT_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否可售" prop="saleable">
          <el-radio-group v-model="ptDlg.form.saleable">
            <el-radio :value="1">可售</el-radio>
            <el-radio :value="0">不可售</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="计容面积(㎡)" prop="farArea">
          <el-input-number v-model="ptDlg.form.farArea" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="可售面积(㎡)" prop="saleableArea">
          <el-input-number v-model="ptDlg.form.saleableArea" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="ptDlg.form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ptDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="ptDlg.saving" @click="onPtSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 楼栋弹窗 -->
    <el-dialog v-model="bldDlg.visible" :title="bldDlg.form.id ? '编辑楼栋' : '新增楼栋'" width="560px" destroy-on-close>
      <el-form ref="bldFormRef" :model="bldDlg.form" :rules="bldRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="栋号" prop="buildingCode">
              <el-input v-model="bldDlg.form.buildingCode" placeholder="如：1#" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="楼栋名称" prop="name">
              <el-input v-model="bldDlg.form.name" placeholder="如：1号楼" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属分期" prop="phaseId">
              <el-select v-model="bldDlg.form.phaseId" placeholder="不分期可留空" clearable style="width: 100%">
                <el-option v-for="p in phases" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="bldDlg.form.status" style="width: 100%">
                <el-option v-for="(label, value) in BLD_STATUS" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地上层数" prop="floors">
              <el-input-number v-model="bldDlg.form.floors" :min="0" :max="99" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地下层数" prop="undergroundFloors">
              <el-input-number v-model="bldDlg.form.undergroundFloors" :min="0" :max="9" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单元数" prop="units">
              <el-input-number v-model="bldDlg.form.units" :min="1" :max="9" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总面积(㎡)" prop="totalArea">
              <el-input-number v-model="bldDlg.form.totalArea" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="bldDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="bldDlg.saving" @click="onBldSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 房间生成器 -->
    <el-dialog v-model="gen.visible" title="批量生成房间" width="480px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="按楼层 × 单元 × 每层户室批量生成房间，楼层可为负数（地下层）" />
      <el-form ref="genFormRef" :model="gen.form" :rules="genRules" label-width="110px">
        <el-form-item label="楼栋">
          <el-input :model-value="currentBuildingLabel" disabled />
        </el-form-item>
        <el-form-item label="起始楼层" prop="startFloor">
          <el-input-number v-model="gen.form.startFloor" :min="-5" :max="99" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束楼层" prop="endFloor">
          <el-input-number v-model="gen.form.endFloor" :min="-5" :max="99" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单元数" prop="units">
          <el-input-number v-model="gen.form.units" :min="1" :max="9" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="每层户室数" prop="roomsPerFloor">
          <el-input-number v-model="gen.form.roomsPerFloor" :min="1" :max="20" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gen.visible = false">取消</el-button>
        <el-button type="primary" :loading="gen.saving" @click="onGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 房间编辑 -->
    <el-dialog v-model="roomDlg.visible" title="编辑房间" width="520px" destroy-on-close>
      <el-alert v-if="roomDlg.form.saleStatus !== 'INIT'" type="warning" :closable="false" show-icon style="margin-bottom: 12px"
        title="非初始状态的房间禁止修改户型/面积等属性" />
      <el-form ref="roomFormRef" :model="roomDlg.form" :rules="roomRules" label-width="100px">
        <el-form-item label="房间编号">
          <el-input :model-value="roomDlg.form.roomCode" disabled />
        </el-form-item>
        <el-form-item label="户型" prop="layout">
          <el-input v-model="roomDlg.form.layout" :disabled="roomDlg.form.saleStatus !== 'INIT'" placeholder="如：三房两厅" maxlength="32" />
        </el-form-item>
        <el-form-item label="朝向" prop="orientation">
          <el-input v-model="roomDlg.form.orientation" :disabled="roomDlg.form.saleStatus !== 'INIT'" placeholder="如：南北" maxlength="32" />
        </el-form-item>
        <el-form-item label="建筑面积(㎡)" prop="area">
          <el-input-number v-model="roomDlg.form.area" :min="0" :precision="2" :controls="false" :disabled="roomDlg.form.saleStatus !== 'INIT'" style="width: 100%" />
        </el-form-item>
        <el-form-item label="套内面积(㎡)" prop="innerArea">
          <el-input-number v-model="roomDlg.form.innerArea" :min="0" :precision="2" :controls="false" :disabled="roomDlg.form.saleStatus !== 'INIT'" style="width: 100%" />
        </el-form-item>
        <el-form-item label="销控状态" prop="saleStatus">
          <el-select v-model="roomDlg.form.saleStatus" style="width: 100%">
            <el-option v-for="(label, value) in SALE_STATUS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="roomDlg.saving" @click="onRoomSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, MagicStick } from '@element-plus/icons-vue'
import {
  projectPage, projectDetail, projectCreate, projectUpdate, projectChangeStatus, projectDelete,
  phaseList, phaseCreate, phaseUpdate, phaseDelete,
  productTypeList, productTypeCreate, productTypeUpdate, productTypeDelete,
  buildingList, buildingCreate, buildingUpdate, buildingDelete,
  roomPage, roomGenerate, roomUpdate, roomDelete
} from '../../../api/mdm'
import { orgTree } from '../../../api/mdm'
import { money, statusColor } from '../../../utils/format'

const PRJ_STATUS = { PREPARING: '筹备', CONSTRUCTION: '在建', DELIVERED: '交付', LIQUIDATION: '清算' }
const BLD_STATUS = { PLANNED: '规划', CONSTRUCTING: '在建', COMPLETED: '竣工' }
const BLD_STATUS_TAG = { PLANNED: 'info', CONSTRUCTING: 'warning', COMPLETED: 'success' }
const SALE_STATUS = { INIT: '初始', LOCKED: '锁定', SOLD: '售出' }
const SALE_STATUS_TAG = { INIT: 'info', LOCKED: 'warning', SOLD: 'success' }
const COOP_MODES = ['独资', '合作', '代建']
const PT_CATEGORIES = ['住宅', '商业', '车位', '配套']
const STATUS_FLOW = { PREPARING: 'CONSTRUCTION', CONSTRUCTION: 'DELIVERED', DELIVERED: 'LIQUIDATION' }
const nextStatus = s => STATUS_FLOW[s] || null

const loading = ref(false)
const rows = ref([])
const orgs = ref([])
const orgOptions = ref([])
const orgMap = ref({})
const query = reactive({ kw: '', status: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const orgName = id => orgMap.value[id] || '-'

const flattenOrg = (list, map) => (list || []).forEach(o => {
  map[o.id] = o.name
  flattenOrg(o.children, map)
})

const load = async () => {
  loading.value = true
  try {
    const data = await projectPage({
      current: page.current, size: page.size,
      kw: query.kw || undefined,
      status: query.status || undefined
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.kw = ''
  query.status = ''
  page.current = 1
  load()
}

// ================= 项目弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = { name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }] }

const openDialog = async row => {
  if (row) {
    const detail = await projectDetail(row.id)
    dlg.form = { ...detail }
  } else {
    dlg.form = {
      name: '', orgId: null, city: '', address: '', cooperationMode: '', partner: '',
      landAcquiredDate: null, landArea: null, farArea: null, saleableArea: null,
      plotRatio: null, landCost: null, status: 'PREPARING', remark: ''
    }
  }
  dlg.visible = true
}

const onSave = async () => {
  await formRef.value.validate()
  dlg.saving = true
  try {
    if (dlg.form.id) {
      await projectUpdate(dlg.form)
      ElMessage.success('修改成功')
    } else {
      await projectCreate(dlg.form)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除项目「${row.name}」吗？删除后其楼栋/房间等主数据将一并失效。`, '删除确认', { type: 'warning' })
    .then(async () => {
      await projectDelete(row.id)
      ElMessage.success('删除成功')
      load()
    }).catch(() => {})
}

const onChangeStatus = row => {
  const next = nextStatus(row.status)
  ElMessageBox.confirm(`确定将项目状态流转为「${PRJ_STATUS[next]}」吗？流转路径：筹备 → 在建 → 交付 → 清算。`, '状态流转', { type: 'warning' })
    .then(async () => {
      await projectChangeStatus(row.id, next)
      ElMessage.success('状态已流转')
      drawer.value.project = { ...row, status: next }
      load()
    }).catch(() => {})
}

// ================= 详情抽屉 =================
const drawer = ref({ visible: false, loading: false, project: {}, tab: 'phases' })
const phases = ref([])
const productTypes = ref([])
const buildings = ref([])
const rooms = ref([])
const roomLoading = ref(false)
const roomQuery = reactive({ buildingId: null, saleStatus: '' })
const roomPager = reactive({ current: 1, size: 20, total: 0 })

const phaseName = id => phases.value.find(p => p.id === id)?.name || '-'
const currentBuildingLabel = computed(() => {
  const b = buildings.value.find(x => x.id === roomQuery.buildingId)
  return b ? `${b.buildingCode} ${b.name || ''}` : '请先选择楼栋'
})

const openDetail = async row => {
  drawer.value.project = await projectDetail(row.id)
  drawer.value.tab = 'phases'
  drawer.value.visible = true
  await loadTabs()
  roomQuery.buildingId = buildings.value[0]?.id || null
  roomQuery.saleStatus = ''
  roomPager.current = 1
  loadRooms()
}

const loadTabs = async () => {
  drawer.value.loading = true
  try {
    const id = drawer.value.project.id
    const [ps, pts, bs] = await Promise.all([phaseList(id), productTypeList(id), buildingList(id)])
    phases.value = ps || []
    productTypes.value = pts || []
    buildings.value = bs || []
  } finally {
    drawer.value.loading = false
  }
}

const loadRooms = async () => {
  roomLoading.value = true
  try {
    const data = await roomPage({
      current: roomPager.current, size: roomPager.size,
      projectId: drawer.value.project.id,
      buildingId: roomQuery.buildingId || undefined,
      saleStatus: roomQuery.saleStatus || undefined
    })
    rooms.value = data.records || []
    roomPager.total = Number(data.total || 0)
  } finally {
    roomLoading.value = false
  }
}

// ---- 分期 ----
const phaseFormRef = ref()
const phaseDlg = reactive({ visible: false, saving: false, form: {} })
const phaseRules = {
  phaseCode: [{ required: true, message: '请输入分期编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入分期名称', trigger: 'blur' }]
}
const openPhaseDialog = row => {
  phaseDlg.form = row ? { ...row } : { phaseCode: '', name: '', farArea: null, saleableArea: null, status: 'PREPARING' }
  phaseDlg.visible = true
}
const onPhaseSave = async () => {
  await phaseFormRef.value.validate()
  phaseDlg.saving = true
  try {
    const pid = drawer.value.project.id
    if (phaseDlg.form.id) await phaseUpdate(pid, phaseDlg.form.id, phaseDlg.form)
    else await phaseCreate(pid, phaseDlg.form)
    ElMessage.success('保存成功')
    phaseDlg.visible = false
    loadTabs()
  } finally {
    phaseDlg.saving = false
  }
}
const onPhaseDelete = row => {
  ElMessageBox.confirm(`确定删除分期「${row.name}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await phaseDelete(drawer.value.project.id, row.id)
      ElMessage.success('删除成功')
      loadTabs()
    }).catch(() => {})
}

// ---- 业态 ----
const ptFormRef = ref()
const ptDlg = reactive({ visible: false, saving: false, form: {} })
const ptRules = { name: [{ required: true, message: '请输入业态名称', trigger: 'blur' }] }
const openPtDialog = row => {
  ptDlg.form = row
    ? { ...row }
    : { name: '', category: '', saleable: 1, farArea: null, saleableArea: null, sort: 0 }
  ptDlg.visible = true
}
const onPtSave = async () => {
  await ptFormRef.value.validate()
  ptDlg.saving = true
  try {
    const pid = drawer.value.project.id
    if (ptDlg.form.id) await productTypeUpdate(pid, ptDlg.form.id, ptDlg.form)
    else await productTypeCreate(pid, ptDlg.form)
    ElMessage.success('保存成功')
    ptDlg.visible = false
    loadTabs()
  } finally {
    ptDlg.saving = false
  }
}
const onPtDelete = row => {
  ElMessageBox.confirm(`确定删除业态「${row.name}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await productTypeDelete(drawer.value.project.id, row.id)
      ElMessage.success('删除成功')
      loadTabs()
    }).catch(() => {})
}

// ---- 楼栋 ----
const bldFormRef = ref()
const bldDlg = reactive({ visible: false, saving: false, form: {} })
const bldRules = {
  buildingCode: [{ required: true, message: '请输入栋号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入楼栋名称', trigger: 'blur' }]
}
const openBuildingDialog = row => {
  bldDlg.form = row
    ? { ...row }
    : { buildingCode: '', name: '', phaseId: null, floors: 18, undergroundFloors: 0, units: 1, totalArea: null, status: 'PLANNED' }
  bldDlg.visible = true
}
const onBldSave = async () => {
  await bldFormRef.value.validate()
  bldDlg.saving = true
  try {
    const pid = drawer.value.project.id
    if (bldDlg.form.id) await buildingUpdate(pid, bldDlg.form.id, bldDlg.form)
    else await buildingCreate(pid, bldDlg.form)
    ElMessage.success('保存成功')
    bldDlg.visible = false
    await loadTabs()
    if (!roomQuery.buildingId && buildings.value.length) {
      roomQuery.buildingId = buildings.value[buildings.value.length - 1].id
      loadRooms()
    }
  } finally {
    bldDlg.saving = false
  }
}
const onBuildingDelete = row => {
  ElMessageBox.confirm(`确定删除楼栋「${row.buildingCode}」吗？楼栋下存在房间时将无法删除。`, '删除确认', { type: 'warning' })
    .then(async () => {
      await buildingDelete(drawer.value.project.id, row.id)
      ElMessage.success('删除成功')
      if (roomQuery.buildingId === row.id) roomQuery.buildingId = null
      loadTabs()
      loadRooms()
    }).catch(() => {})
}

// ---- 房间生成与编辑 ----
const genFormRef = ref()
const gen = reactive({
  visible: false, saving: false,
  form: { startFloor: 1, endFloor: 18, units: 1, roomsPerFloor: 4 }
})
const genRules = {
  startFloor: [{ required: true, message: '请填写起始楼层', trigger: 'blur' }],
  endFloor: [{ required: true, message: '请填写结束楼层', trigger: 'blur' }],
  roomsPerFloor: [{ required: true, message: '请填写每层户室数', trigger: 'blur' }]
}
const onGenerate = async () => {
  await genFormRef.value.validate()
  if (gen.form.startFloor > gen.form.endFloor) {
    return ElMessage.warning('起始楼层不能大于结束楼层')
  }
  gen.saving = true
  try {
    const result = await roomGenerate({ ...gen.form, buildingId: roomQuery.buildingId })
    const cnt = result?.created ?? result?.total ?? 0
    ElMessage.success(`房间生成完成，本次生成 ${cnt} 间`)
    gen.visible = false
    loadRooms()
  } finally {
    gen.saving = false
  }
}

const roomFormRef = ref()
const roomDlg = reactive({ visible: false, saving: false, form: {} })
const roomRules = {}
const openRoomDialog = row => {
  roomDlg.form = { ...row }
  roomDlg.visible = true
}
const onRoomSave = async () => {
  await roomFormRef.value.validate()
  roomDlg.saving = true
  try {
    await roomUpdate(roomDlg.form)
    ElMessage.success('保存成功')
    roomDlg.visible = false
    loadRooms()
  } finally {
    roomDlg.saving = false
  }
}
const onRoomDelete = row => {
  ElMessageBox.confirm(`确定删除房间「${row.roomCode}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await roomDelete(row.id)
      ElMessage.success('删除成功')
      loadRooms()
    }).catch(() => {})
}

onMounted(async () => {
  load()
  orgOptions.value = await orgTree()
  const map = {}
  flattenOrg(orgOptions.value, map)
  orgMap.value = map
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
.drawer-header { display: flex; align-items: center; }
.drawer-header .title { font-size: 15px; font-weight: 700; color: #0c447c; margin-right: 10px; }
.info-block { margin-bottom: 14px; }
.detail-tabs { padding: 0 4px; }
.tab-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.tab-toolbar .tab-title { font-weight: 600; color: #0c447c; margin-right: auto; }
</style>
