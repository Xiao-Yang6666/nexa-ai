#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
S2 结构化表生成器（机器权威源）。
FUNCTIONS 列表是唯一事实源：每条记录手工填写真实证据/验收，禁止模板填充。
产出：
  - FUNCTION-LIST.csv
  - REQUIREMENT-TREE.csv（含功能型 + NFR/Compliance/RBAC 三维度节点）
  - FUNCTION-LIST.md（人类可读）
并打印自检统计。
"""
import csv, os, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))

# 每条功能项字段顺序（与 CSV 列对齐）：
# function_id, function, capability_module, platform, roles, priority, trigger,
# core_result, data_objects, evidence, acceptance_focus,
# state_exception_permission_rule, source_fc
FUNCTIONS = []

def F(fid, name, module, platform, roles, prio, trigger, core, data, ev, acc, rule, fc):
    FUNCTIONS.append(dict(
        function_id=fid, function=name, capability_module=module, platform=platform,
        roles=roles, priority=prio, trigger=trigger, core_result=core,
        data_objects=data, evidence=ev, acceptance_focus=acc,
        state_exception_permission_rule=rule, source_fc=fc))

# ====== 数据将由后续 append 脚本注入到 FUNCTIONS（通过 import 后调用 build()）======
def build():
    pass

if __name__ == "__main__":
    print("skeleton only; data injected by sibling modules")
