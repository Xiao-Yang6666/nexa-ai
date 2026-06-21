'use client';

import { ConsoleShell } from '@/features/console';
import { useSelfTasks, type TaskUiStatus, type TaskVM } from '../model/relay.model';
import styles from './TasksPage.module.css';

function ProgressBar({ progress, failed }: { progress: number; failed: boolean }) {
  return (
    <div className={styles.prog}>
      <div className={styles.progTrack}>
        <div
          className={`${styles.progFill}${failed ? ` ${styles.fail}` : ''}`}
          style={{ width: `${progress}%` }}
        />
      </div>
      <span className={styles.progTxt}>{progress}%</span>
    </div>
  );
}

const STATUS_MAP: Record<TaskUiStatus, { badge: string; label: string; color: string }> = {
  queued: { badge: 'b-neutral', label: '排队中', color: '--color-text-muted' },
  running: { badge: 'b-info', label: '处理中', color: '--color-info' },
  done: { badge: 'b-suc', label: '已完成', color: '--color-success' },
  failed: { badge: 'b-dan', label: '失败', color: '--color-danger' },
};

/**
 * TasksPage — 异步任务列表（S6 console/tasks.html 工程化）。
 * 接 GET /api/task/self（F-2003 自助任务列表）。
 * 客户端零泄露：仅展示任务 ID/类型/状态/进度/失败原因。
 */
export function TasksPage() {
  const { data, isLoading, isError, refetch } = useSelfTasks();

  return (
    <ConsoleShell activeId="tasks" title="异步任务" crumb={['控制台', '异步任务']}>
      <div className={`${styles.intro} nx-fade`}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6}>
          <circle cx="12" cy="12" r="9" />
          <path d="M12 8v4l3 2" />
        </svg>
        <p>
          批量与长耗时 API 任务（如<b>批量 Embedding</b>、<b>长文档解析</b>、<b>批量推理</b>
          ）在此异步执行。任务完成后结果可下载，失败可查看错误详情并重试。结果文件保留<b> 7 天</b>。
        </p>
      </div>

      {isLoading ? (
        <div className={styles.tableCard}>
          <div className={styles.skRow} />
          <div className={styles.skRow} />
          <div className={styles.skRow} />
        </div>
      ) : isError || !data ? (
        <div className={`${styles.tableCard} ${styles.stateBox}`}>
          <div className={styles.t}>任务列表加载失败</div>
          <div>网络或服务异常，请稍后重试。</div>
          <button className="btn btn-sec" style={{ marginTop: 'var(--space-4)' }} onClick={() => refetch()}>
            重试
          </button>
        </div>
      ) : data.items.length === 0 ? (
        <div className={`${styles.tableCard} ${styles.stateBox}`}>
          <div className={styles.t}>暂无异步任务</div>
          <div>通过 API 创建批量任务后，可在此处查看进度与结果。</div>
        </div>
      ) : (
        <div className={`${styles.tableCard} nx-fade`}>
          <div className={styles.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>任务 ID</th>
                  <th>类型</th>
                  <th>提交时间</th>
                  <th>状态</th>
                  <th>进度</th>
                  <th>结果 / 错误</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((task: TaskVM) => {
                  const st = STATUS_MAP[task.status];
                  return (
                    <tr key={task.id}>
                      <td className={styles.tid}>{task.taskId}</td>
                      <td>{task.typeName}</td>
                      <td className="mono-num">{task.submitTime}</td>
                      <td>
                        <span className={`badge ${st.badge}`}>
                          <span className="dot" style={{ background: `var(${st.color})` }} />
                          {st.label}
                        </span>
                      </td>
                      <td>
                        {task.status === 'queued' ? (
                          <span className="muted">—</span>
                        ) : (
                          <ProgressBar progress={task.progress} failed={task.status === 'failed'} />
                        )}
                      </td>
                      <td>
                        {task.status === 'done' ? (
                          <button className={styles.resultDl} type="button">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6}>
                              <path d="M12 3v12M7 11l5 4 5-4M5 20h14" />
                            </svg>
                            下载结果
                          </button>
                        ) : task.status === 'failed' ? (
                          <span className={styles.resultErr}>
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6}>
                              <circle cx="12" cy="12" r="9" />
                              <path d="M12 8v5M12 16h.01" />
                            </svg>
                            {task.failReason || '执行失败'}
                          </span>
                        ) : (
                          <span className="muted">—</span>
                        )}
                      </td>
                      <td>
                        <div className={styles.rowacts}>
                          {(task.status === 'running' || task.status === 'queued') && (
                            <button className={`${styles.iconact} ${styles.dan}`} type="button" title="取消">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6}>
                                <circle cx="12" cy="12" r="9" />
                                <path d="M9 9l6 6M15 9l-6 6" />
                              </svg>
                            </button>
                          )}
                          {task.status === 'failed' && (
                            <button className={styles.iconact} type="button" title="重试">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6}>
                                <path d="M4 12a8 8 0 1 1 2.3 5.6M4 19v-5h5" />
                              </svg>
                            </button>
                          )}
                          <button className={styles.iconact} type="button" title="查看详情">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6}>
                              <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7z" />
                              <circle cx="12" cy="12" r="3" />
                            </svg>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className={styles.pager}>
            <span>
              共 <b style={{ color: 'var(--color-text)' }}>{data.total}</b> 个任务
            </span>
          </div>
        </div>
      )}
    </ConsoleShell>
  );
}
