import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import {
  AlertCircle,
  BookOpen,
  CheckCircle2,
  ClipboardCheck,
  Code2,
  FileText,
  Gauge,
  LogOut,
  ListChecks,
  NotebookPen,
  RefreshCw,
  Save,
  Search,
  TimerReset
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

type LayerKey = 'theory' | 'demo' | 'check' | 'project';
type ViewKey = 'dashboard' | 'study' | 'reviews';

type ChapterProgress = Record<LayerKey | 'review', boolean>;

type ChapterMeta = {
  no: number;
  title: string;
  summary: string;
  priority: string;
  hours: string;
  folder: string;
  finished: string;
  progress: ChapterProgress;
};

type ChapterContent = ChapterMeta & {
  content: Record<LayerKey, string>;
};

type ReviewTask = {
  id: string;
  chapterNo: number;
  title: string;
  round: string;
  dueDate: string;
  status: string;
  due: boolean;
};

type StudyLog = {
  date: string;
  chapter: string;
  layer: string;
  action: string;
  minutes: string;
  reflection: string;
};

type Summary = {
  current: string;
  completed: number;
  total: number;
  percent: number;
  currentChapter?: ChapterMeta;
  dueReviews: ReviewTask[];
  recentLogs: StudyLog[];
};

type UserDto = {
  id: number;
  username: string;
};

type AuthResponse = {
  token: string;
  user: UserDto;
};

const layerMeta: Array<{ key: LayerKey; label: string; icon: typeof BookOpen }> = [
  { key: 'theory', label: '理论', icon: BookOpen },
  { key: 'demo', label: 'Demo', icon: Code2 },
  { key: 'check', label: '自测', icon: ClipboardCheck },
  { key: 'project', label: '项目任务', icon: ListChecks }
];

async function api<T>(url: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem('java-study-token');
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    ...init
  });
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      localStorage.removeItem('java-study-token');
    }
    const data = await response.json().catch(() => ({}));
    throw new Error(data.error || `Request failed: ${response.status}`);
  }
  return response.json();
}

function priorityClass(priority: string) {
  if (priority.includes('L1')) return 'priority l1';
  if (priority.includes('L2')) return 'priority l2';
  return 'priority l3';
}

function getInitialView(): ViewKey {
  const viewParam = new URLSearchParams(window.location.search).get('view');
  return viewParam === 'study' || viewParam === 'reviews' ? viewParam : 'dashboard';
}

function getInitialChapter() {
  const chapterParam = Number(new URLSearchParams(window.location.search).get('chapter'));
  return Number.isInteger(chapterParam) && chapterParam >= 1 && chapterParam <= 60 ? chapterParam : 1;
}

function App() {
  const [authUser, setAuthUser] = useState<UserDto | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [view, setView] = useState<ViewKey>(getInitialView);
  const [chapters, setChapters] = useState<ChapterMeta[]>([]);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [selectedNo, setSelectedNo] = useState(getInitialChapter);
  const [activeLayer, setActiveLayer] = useState<LayerKey>('theory');
  const [chapter, setChapter] = useState<ChapterContent | null>(null);
  const [reviews, setReviews] = useState<ReviewTask[]>([]);
  const [query, setQuery] = useState('');
  const [note, setNote] = useState('');
  const [answer, setAnswer] = useState('');
  const [showAnswer, setShowAnswer] = useState(false);
  const [mistakeReason, setMistakeReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  async function refresh() {
    const [nextSummary, nextChapters, nextReviews] = await Promise.all([
      api<Summary>('/api/summary'),
      api<ChapterMeta[]>('/api/chapters'),
      api<ReviewTask[]>('/api/reviews')
    ]);
    setSummary(nextSummary);
    setChapters(nextChapters);
    setReviews(nextReviews);
    if (!chapter && nextSummary.currentChapter) {
      setSelectedNo(nextSummary.currentChapter.no);
    }
  }

  useEffect(() => {
    const token = localStorage.getItem('java-study-token');
    if (!token) {
      setAuthReady(true);
      return;
    }
    api<UserDto>('/api/auth/me')
      .then((user) => setAuthUser(user))
      .catch((error) => {
        setMessage(error.message);
        localStorage.removeItem('java-study-token');
      })
      .finally(() => setAuthReady(true));
  }, []);

  useEffect(() => {
    if (authUser) {
      refresh().catch((error) => setMessage(error.message));
    }
  }, [authUser]);

  useEffect(() => {
    const params = new URLSearchParams();
    if (view !== 'dashboard') params.set('view', view);
    if (view === 'study') params.set('chapter', String(selectedNo));
    const nextUrl = params.toString() ? `?${params.toString()}` : window.location.pathname;
    window.history.replaceState(null, '', nextUrl);
  }, [view, selectedNo]);

  useEffect(() => {
    if (!authUser) return;
    api<ChapterContent>(`/api/chapters/${selectedNo}`)
      .then((data) => {
        setChapter(data);
        setNote('');
        setAnswer('');
        setShowAnswer(false);
        setMistakeReason('');
      })
      .catch((error) => setMessage(error.message));
  }, [selectedNo, authUser]);

  const filteredChapters = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return chapters;
    return chapters.filter((item) =>
      `${item.no} ${item.title} ${item.summary} ${item.priority}`.toLowerCase().includes(keyword)
    );
  }, [chapters, query]);

  const dueReviews = reviews.filter((item) => item.due);
  const currentContent = chapter?.content[activeLayer] ?? '';
  const answerContent = activeLayer === 'check' ? currentContent.split('## 参考答案')[1] : '';
  const questionContent = activeLayer === 'check' ? currentContent.split('## 参考答案')[0] : currentContent;

  async function runAction(action: () => Promise<void>, success: string) {
    setBusy(true);
    setMessage('');
    try {
      await action();
      await refresh();
      if (chapter) {
        setChapter(await api<ChapterContent>(`/api/chapters/${chapter.no}`));
      }
      setMessage(success);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '操作失败');
    } finally {
      setBusy(false);
    }
  }

  function openChapter(no: number, targetView: ViewKey = 'study') {
    setSelectedNo(no);
    setView(targetView);
  }

  function switchView(nextView: ViewKey) {
    setView(nextView);
  }

  function handleAuth(response: AuthResponse) {
    localStorage.setItem('java-study-token', response.token);
    setAuthUser(response.user);
  }

  function logout() {
    localStorage.removeItem('java-study-token');
    setAuthUser(null);
    setSummary(null);
    setChapters([]);
    setReviews([]);
    setChapter(null);
  }

  const markLayerDone = () => {
    if (!chapter) return;
    runAction(
      () =>
        api('/api/progress', {
          method: 'PATCH',
          body: JSON.stringify({ chapterNo: chapter.no, layer: activeLayer, done: true })
        }),
      '进度已更新'
    );
  };

  const saveNote = () => {
    if (!chapter || !note.trim()) return;
    runAction(
      () =>
        api('/api/notes', {
          method: 'POST',
          body: JSON.stringify({ chapterNo: chapter.no, title: chapter.title, body: note })
        }),
      '笔记已保存'
    );
    setNote('');
  };

  const submitCheck = (passed: boolean) => {
    if (!chapter || !answer.trim()) return;
    runAction(
      () =>
        api('/api/check-result', {
          method: 'POST',
          body: JSON.stringify({
            chapterNo: chapter.no,
            chapterTitle: chapter.title,
            answer,
            passed,
            symptom: '自测自评未通过',
            reason: mistakeReason || '答案不完整或关键概念不稳',
            fix: '回看理论与 Demo，重新完成本节自测'
          })
        }),
      passed ? '自测记录已保存' : '自测记录与错题已保存'
    );
    setAnswer('');
    setMistakeReason('');
  };

  const completeReview = (chapterNo: number) => {
    runAction(
      () =>
        api('/api/reviews', {
          method: 'PATCH',
          body: JSON.stringify({ chapterNo })
        }),
      '复习已完成'
    );
  };

  const toggleAnswer = () => {
    if (!chapter) return;
    const next = !showAnswer;
    setShowAnswer(next);
    if (next) {
      api('/api/check-reveals', {
        method: 'POST',
        body: JSON.stringify({ chapterNo: chapter.no, chapterTitle: chapter.title })
      }).catch((error) => setMessage(error.message));
    }
  };

  if (!authReady) {
    return <div className="auth-loading">正在读取账号状态...</div>;
  }

  if (!authUser) {
    return <AuthScreen onAuthed={handleAuth} message={message} setMessage={setMessage} />;
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">J</div>
          <div>
            <h1>Java 60 天</h1>
            <p>{authUser.username}</p>
          </div>
          <button className="logout-button" onClick={logout} title="退出登录">
            <LogOut size={18} />
          </button>
        </div>

        <nav className="nav">
          <button className={view === 'dashboard' ? 'active' : ''} onClick={() => switchView('dashboard')}>
            <Gauge size={18} /> 总览
          </button>
          <button className={view === 'study' ? 'active' : ''} onClick={() => switchView('study')}>
            <BookOpen size={18} /> 学习
          </button>
          <button className={view === 'reviews' ? 'active' : ''} onClick={() => switchView('reviews')}>
            <TimerReset size={18} /> 复习
          </button>
        </nav>

        <div className="search-box">
          <Search size={16} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索章节" />
        </div>

        <div className="chapter-list">
          {filteredChapters.map((item) => {
            const done = item.progress.theory && item.progress.demo && item.progress.check && item.progress.project;
            return (
              <button
                key={item.no}
                className={`chapter-link ${selectedNo === item.no ? 'selected' : ''}`}
                onClick={() => openChapter(item.no)}
              >
                <span className="chapter-no">{String(item.no).padStart(2, '0')}</span>
                <span className="chapter-title">{item.title}</span>
                {done ? <CheckCircle2 size={16} className="done-icon" /> : null}
              </button>
            );
          })}
        </div>
      </aside>

      <main className="workspace">
        {message ? (
          <div className="toast">
            <AlertCircle size={16} />
            {message}
          </div>
        ) : null}

        {view === 'dashboard' ? (
          <Dashboard summary={summary} chapters={chapters} dueReviews={dueReviews} onOpenChapter={openChapter} />
        ) : null}

        {view === 'study' && chapter ? (
          <section className="study-layout">
            <div className="reader">
              <header className="reader-header">
                <div>
                  <div className="eyebrow">Day {String(chapter.no).padStart(2, '0')}</div>
                  <h2>{chapter.title}</h2>
                  <p>{chapter.summary}</p>
                </div>
                <div className="chapter-meta">
                  <span className={priorityClass(chapter.priority)}>{chapter.priority}</span>
                  <span>{chapter.hours}</span>
                </div>
              </header>

              <div className="layer-tabs">
                {layerMeta.map((item) => {
                  const Icon = item.icon;
                  return (
                    <button
                      key={item.key}
                      className={activeLayer === item.key ? 'active' : ''}
                      onClick={() => setActiveLayer(item.key)}
                    >
                      <Icon size={17} />
                      {item.label}
                      {chapter.progress[item.key] ? <CheckCircle2 size={15} /> : null}
                    </button>
                  );
                })}
              </div>

              <article className="markdown-body">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {activeLayer === 'check' && !showAnswer ? questionContent : currentContent}
                </ReactMarkdown>
              </article>
            </div>

            <aside className="coach-panel">
              <div className="step-status">
                {layerMeta.map((item) => (
                  <div key={item.key} className={chapter.progress[item.key] ? 'done' : ''}>
                    <span>{item.label}</span>
                    <CheckCircle2 size={16} />
                  </div>
                ))}
              </div>

              <button className="primary-action" disabled={busy || chapter.progress[activeLayer]} onClick={markLayerDone}>
                <CheckCircle2 size={18} />
                标记当前层完成
              </button>

              <section className="side-section">
                <h3><NotebookPen size={17} /> 学习笔记</h3>
                <textarea value={note} onChange={(event) => setNote(event.target.value)} placeholder="记录关键理解、卡点或复盘" />
                <button disabled={busy || !note.trim()} onClick={saveNote}>
                  <Save size={17} />
                  保存笔记
                </button>
              </section>

              {activeLayer === 'check' ? (
                <section className="side-section">
                  <h3><ClipboardCheck size={17} /> 自测闭环</h3>
                  <textarea value={answer} onChange={(event) => setAnswer(event.target.value)} placeholder="先写自己的答案" />
                  <button disabled={!answerContent} onClick={toggleAnswer}>
                    <FileText size={17} />
                    {showAnswer ? '隐藏参考答案' : '显示参考答案'}
                  </button>
                  <input
                    value={mistakeReason}
                    onChange={(event) => setMistakeReason(event.target.value)}
                    placeholder="未通过原因"
                  />
                  <div className="button-row">
                    <button disabled={busy || !answer.trim()} onClick={() => submitCheck(true)}>通过</button>
                    <button disabled={busy || !answer.trim()} onClick={() => submitCheck(false)}>需复习</button>
                  </div>
                </section>
              ) : null}
            </aside>
          </section>
        ) : null}

        {view === 'reviews' ? (
          <Reviews reviews={reviews} onOpenChapter={openChapter} onComplete={completeReview} busy={busy} />
        ) : null}
      </main>
    </div>
  );
}

function AuthScreen({
  onAuthed,
  message,
  setMessage
}: {
  onAuthed: (response: AuthResponse) => void;
  message: string;
  setMessage: (message: string) => void;
}) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [secret, setSecret] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage('');
    try {
      const response = await api<AuthResponse>(`/api/auth/${mode}`, {
        method: 'POST',
        body: JSON.stringify({ username, secret })
      });
      onAuthed(response);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '认证失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <div className="brand auth-brand">
          <div className="brand-mark">J</div>
          <div>
            <h1>Java 60 天</h1>
            <p>账号维度学习工作台</p>
          </div>
        </div>
        <div className="auth-tabs">
          <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>登录</button>
          <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>注册</button>
        </div>
        <form onSubmit={submit} className="auth-form">
          <label>
            账号
            <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="例如 java_runner" />
          </label>
          <label>
            密钥
            <input
              value={secret}
              onChange={(event) => setSecret(event.target.value)}
              placeholder="至少 16 位"
              type="password"
            />
          </label>
          <button disabled={busy || !username || !secret}>{mode === 'login' ? '登录' : '创建账号'}</button>
        </form>
        {message ? <p className="auth-message">{message}</p> : null}
      </section>
    </main>
  );
}

function Dashboard({
  summary,
  chapters,
  dueReviews,
  onOpenChapter
}: {
  summary: Summary | null;
  chapters: ChapterMeta[];
  dueReviews: ReviewTask[];
  onOpenChapter: (no: number, view?: ViewKey) => void;
}) {
  const stageStats = [
    { label: 'Java SE', range: [1, 20] },
    { label: '工程化与 Spring', range: [21, 40] },
    { label: '进阶与面试', range: [41, 60] }
  ].map((stage) => {
    const rows = chapters.filter((item) => item.no >= stage.range[0] && item.no <= stage.range[1]);
    const done = rows.filter((item) => item.progress.theory && item.progress.demo && item.progress.check && item.progress.project).length;
    return { ...stage, done, total: rows.length };
  });

  return (
    <section className="dashboard">
      <header className="dashboard-hero">
        <div>
          <div className="eyebrow">当前进度</div>
          <h2>{summary?.current ?? '读取中'}</h2>
          <p>完成 {summary?.completed ?? 0} / {summary?.total ?? 60} 天</p>
        </div>
        <div className="progress-ring" style={{ '--value': `${summary?.percent ?? 0}%` } as React.CSSProperties}>
          <span>{summary?.percent ?? 0}%</span>
        </div>
      </header>

      <div className="metric-grid">
        <div className="metric">
          <span>今日复习</span>
          <strong>{dueReviews.length}</strong>
        </div>
        <div className="metric">
          <span>当前章节</span>
          <strong>{summary?.currentChapter ? String(summary.currentChapter.no).padStart(2, '0') : '--'}</strong>
        </div>
        <div className="metric">
          <span>学习日志</span>
          <strong>{summary?.recentLogs.length ?? 0}</strong>
        </div>
      </div>

      <div className="dashboard-grid">
        <section className="panel">
          <h3>阶段进度</h3>
          {stageStats.map((stage) => (
            <div className="stage-row" key={stage.label}>
              <span>{stage.label}</span>
              <div className="bar"><i style={{ width: `${stage.total ? (stage.done / stage.total) * 100 : 0}%` }} /></div>
              <b>{stage.done}/{stage.total}</b>
            </div>
          ))}
        </section>

        <section className="panel">
          <h3>今日任务</h3>
          {summary?.currentChapter ? (
            <button className="task-row" onClick={() => onOpenChapter(summary.currentChapter!.no, 'study')}>
              <BookOpen size={18} />
              <span>{String(summary.currentChapter.no).padStart(2, '0')} {summary.currentChapter.title}</span>
            </button>
          ) : null}
          {dueReviews.slice(0, 4).map((task) => (
            <button className="task-row" key={task.id} onClick={() => onOpenChapter(task.chapterNo, 'study')}>
              <RefreshCw size={18} />
              <span>{task.round}：{task.title}</span>
            </button>
          ))}
        </section>

        <section className="panel wide">
          <h3>最近记录</h3>
          <div className="log-table">
            {(summary?.recentLogs ?? []).map((log, index) => (
              <div key={`${log.date}-${index}`}>
                <span>{log.date}</span>
                <span>{log.chapter}</span>
                <span>{log.layer}</span>
                <span>{log.action}</span>
              </div>
            ))}
          </div>
        </section>
      </div>
    </section>
  );
}

function Reviews({
  reviews,
  onOpenChapter,
  onComplete,
  busy
}: {
  reviews: ReviewTask[];
  onOpenChapter: (no: number, view?: ViewKey) => void;
  onComplete: (chapterNo: number) => void;
  busy: boolean;
}) {
  return (
    <section className="reviews-page">
      <header className="page-title">
        <div>
          <div className="eyebrow">间隔复习</div>
          <h2>复习队列</h2>
        </div>
      </header>
      <div className="review-list">
        {reviews.map((task) => (
          <div className={`review-item ${task.due ? 'due' : ''}`} key={task.id}>
            <div>
              <span className="review-date">{task.dueDate}</span>
              <h3>{String(task.chapterNo).padStart(2, '0')} {task.title}</h3>
              <p>{task.round} · {task.status}</p>
            </div>
            <div className="button-row">
              <button onClick={() => onOpenChapter(task.chapterNo, 'study')}>打开</button>
              <button disabled={busy} onClick={() => onComplete(task.chapterNo)}>完成</button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

export default App;
