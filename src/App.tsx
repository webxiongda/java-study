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
  Printer,
  RefreshCw,
  Save,
  Search,
  TimerReset
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

type LayerKey = 'theory' | 'demo' | 'check' | 'project';
type ViewKey = 'dashboard' | 'today' | 'study' | 'cards' | 'portfolio' | 'reviews' | 'interview';

type InterviewCategory = {
  category: string;
  label: string;
  total: number;
  attempted: number;
  mastered: number;
};

type InterviewQuestionItem = {
  id: number;
  category: string;
  difficulty: string;
  frequency: string;
  prompt: string;
  relatedChapterNo: number | null;
  relatedChapterTitle: string | null;
  source: string;
  attempted: boolean;
  mastered: boolean;
};

type InterviewAttemptRecord = {
  id: number;
  answer: string;
  selfAssessed: string;
  aiScore: number | null;
  aiFeedback: string | null;
  createdAt: string;
};

type InterviewQuestionDetail = {
  id: number;
  category: string;
  difficulty: string;
  frequency: string;
  prompt: string;
  referenceAnswer: string;
  followUp: string | null;
  relatedChapterNo: number | null;
  relatedChapterTitle: string | null;
  source: string;
  recentAttempts: InterviewAttemptRecord[];
};

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

type ReviewCard = {
  id: number;
  chapterNo: number;
  chapterTitle: string;
  type: string;
  prompt: string;
  expected: string;
  dueDate: string;
  status: string;
};

type TodayPlan = {
  focus: string;
  currentChapter?: ChapterMeta;
  dueReviews: ReviewTask[];
  dueCards: ReviewCard[];
  blocks: string[];
  acceptance: string[];
};

type PortfolioEvidence = {
  id: number;
  chapterNo: number;
  milestone: string;
  evidenceType: string;
  body: string;
  createdAt: string;
};

type Portfolio = {
  totalEvidence: number;
  milestones: string[];
  evidence: PortfolioEvidence[];
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
  return viewParam === 'today' || viewParam === 'study' || viewParam === 'cards' || viewParam === 'portfolio' || viewParam === 'reviews' || viewParam === 'interview' ? viewParam : 'dashboard';
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
  const [today, setToday] = useState<TodayPlan | null>(null);
  const [selectedNo, setSelectedNo] = useState(getInitialChapter);
  const [activeLayer, setActiveLayer] = useState<LayerKey>('theory');
  const [chapter, setChapter] = useState<ChapterContent | null>(null);
  const [reviews, setReviews] = useState<ReviewTask[]>([]);
  const [cards, setCards] = useState<ReviewCard[]>([]);
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [query, setQuery] = useState('');
  const [note, setNote] = useState('');
  const [validationSummary, setValidationSummary] = useState('');
  const [validationEvidence, setValidationEvidence] = useState('');
  const [answer, setAnswer] = useState('');
  const [cardAnswer, setCardAnswer] = useState('');
  const [portfolioEvidence, setPortfolioEvidence] = useState('');
  const [showAnswer, setShowAnswer] = useState(false);
  const [mistakeReason, setMistakeReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  async function refresh() {
    const [nextSummary, nextChapters, nextReviews, nextToday, nextCards, nextPortfolio] = await Promise.all([
      api<Summary>('/api/summary'),
      api<ChapterMeta[]>('/api/chapters'),
      api<ReviewTask[]>('/api/reviews'),
      api<TodayPlan>('/api/today'),
      api<ReviewCard[]>('/api/cards/due'),
      api<Portfolio>('/api/portfolio')
    ]);
    setSummary(nextSummary);
    setChapters(nextChapters);
    setReviews(nextReviews);
    setToday(nextToday);
    setCards(nextCards);
    setPortfolio(nextPortfolio);
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
        setValidationSummary('');
        setValidationEvidence('');
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
    if (activeLayer === 'check') {
      setMessage('自测层需要先提交自己的答案，通过后会自动完成');
      return;
    }
    runAction(
      () =>
        api('/api/validations', {
          method: 'POST',
          body: JSON.stringify({
            chapterNo: chapter.no,
            layer: activeLayer,
            summary: validationSummary,
            evidence: validationEvidence
          })
        }),
      '验收已保存，进度已更新'
    );
    setValidationSummary('');
    setValidationEvidence('');
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

  const answerReviewCard = (cardId: number, remembered: boolean) => {
    runAction(
      async () => {
        const nextCards = await api<ReviewCard[]>(`/api/cards/${cardId}/answer`, {
          method: 'POST',
          body: JSON.stringify({ answer: cardAnswer, remembered })
        });
        setCards(nextCards);
      },
      remembered ? '复习卡已完成' : '已加入明日加练'
    );
    setCardAnswer('');
  };

  const savePortfolioEvidence = () => {
    if (!chapter || !portfolioEvidence.trim()) return;
    runAction(
      async () => {
        const nextPortfolio = await api<Portfolio>('/api/portfolio/evidence', {
          method: 'POST',
          body: JSON.stringify({
            chapterNo: chapter.no,
            milestone: `Day ${String(chapter.no).padStart(2, '0')} ${chapter.title}`,
            evidenceType: 'manual',
            body: portfolioEvidence
          })
        });
        setPortfolio(nextPortfolio);
      },
      '作品集证据已保存'
    );
    setPortfolioEvidence('');
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

  const printChapter = () => {
    if (!chapter) return;
    window.requestAnimationFrame(() => window.print());
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
          <button className={view === 'today' ? 'active' : ''} onClick={() => switchView('today')}>
            <ClipboardCheck size={18} /> 今日
          </button>
          <button className={view === 'study' ? 'active' : ''} onClick={() => switchView('study')}>
            <BookOpen size={18} /> 学习
          </button>
          <button className={view === 'cards' ? 'active' : ''} onClick={() => switchView('cards')}>
            <TimerReset size={18} /> 卡片
          </button>
          <button className={view === 'portfolio' ? 'active' : ''} onClick={() => switchView('portfolio')}>
            <ListChecks size={18} /> 作品
          </button>
          <button className={view === 'interview' ? 'active' : ''} onClick={() => switchView('interview')}>
            <Search size={18} /> 面试
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

        {view === 'today' ? (
          <Today today={today} onOpenChapter={openChapter} onSwitchView={switchView} />
        ) : null}

        {view === 'study' && chapter ? (
          <>
            <section className="study-layout">
              <div className="reader">
                <header className="reader-header">
                  <div>
                    <div className="eyebrow">Day {String(chapter.no).padStart(2, '0')}</div>
                    <h2>{chapter.title}</h2>
                    <p>{chapter.summary}</p>
                  </div>
                  <div className="reader-actions">
                    <div className="chapter-meta">
                      <span className={priorityClass(chapter.priority)}>{chapter.priority}</span>
                      <span>{chapter.hours}</span>
                    </div>
                    <button className="print-button" onClick={printChapter}>
                      <Printer size={17} />
                      打印自测卷
                    </button>
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
                  {activeLayer === 'check' ? '自测通过后自动完成' : '提交验收并完成'}
                </button>

                <section className="side-section">
                  <h3><ClipboardCheck size={17} /> 强验收</h3>
                  {activeLayer === 'check' ? (
                    <p className="helper-text">自测层需要先写答案，再用下方“通过 / 需复习”完成验收。</p>
                  ) : (
                    <>
                      <textarea value={validationSummary} onChange={(event) => setValidationSummary(event.target.value)} placeholder={activeLayer === 'theory' ? '用 3-5 句话讲清本节核心概念' : '说明你完成了什么、为什么这样做'} />
                      <textarea value={validationEvidence} onChange={(event) => setValidationEvidence(event.target.value)} placeholder={activeLayer === 'demo' ? '运行命令、结果、报错和修复' : activeLayer === 'project' ? '代码链接、接口截图、测试命令或 README 片段' : '可选：补充证据'} />
                    </>
                  )}
                </section>

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
            <PrintSheet chapter={chapter} />
          </>
        ) : null}

        {view === 'reviews' ? (
          <Reviews reviews={reviews} onOpenChapter={openChapter} onComplete={completeReview} busy={busy} />
        ) : null}

        {view === 'cards' ? (
          <Cards cards={cards} answer={cardAnswer} setAnswer={setCardAnswer} onAnswer={answerReviewCard} busy={busy} />
        ) : null}

        {view === 'portfolio' ? (
          <PortfolioView portfolio={portfolio} chapter={chapter} body={portfolioEvidence} setBody={setPortfolioEvidence} onSave={savePortfolioEvidence} busy={busy} />
        ) : null}

        {view === 'interview' ? (
          <InterviewView onOpenChapter={openChapter} />
        ) : null}
      </main>
    </div>
  );
}

type PrintableQuestion = {
  id: string;
  content: string;
};

type PrintableCheckSheet = {
  intro: string;
  questions: PrintableQuestion[];
  answers: string;
};

function removeTopTitle(markdown: string) {
  const lines = markdown.split('\n');
  const firstContentIndex = lines.findIndex((line) => line.trim());
  if (firstContentIndex >= 0 && /^#\s+/.test(lines[firstContentIndex].trim())) {
    lines.splice(firstContentIndex, 1);
  }
  return lines.join('\n').trim();
}

function isQuestionHeading(line: string) {
  return /^#{2,3}\s+Q\d+/i.test(line.trim());
}

function isAnswerMarker(line: string) {
  return /^\s*\*\*参考答案[:：]?\*\*\s*[:：]?/.test(line);
}

function splitQuestions(markdown: string): { intro: string; questions: PrintableQuestion[] } {
  const cleaned = removeTopTitle(markdown)
    .replace(/^##\s*题目\s*$/gm, '')
    .trim();
  const lines = cleaned.split('\n');
  const introLines: string[] = [];
  const questions: PrintableQuestion[] = [];
  let current: string[] = [];

  lines.forEach((line) => {
    if (isQuestionHeading(line)) {
      if (current.length) {
        questions.push({ id: `q-${questions.length + 1}`, content: current.join('\n').trim() });
      }
      current = [line];
      return;
    }
    if (current.length) {
      current.push(line);
    } else {
      introLines.push(line);
    }
  });

  if (current.length) {
    questions.push({ id: `q-${questions.length + 1}`, content: current.join('\n').trim() });
  }

  if (!questions.length && cleaned) {
    questions.push({ id: 'q-1', content: cleaned });
    return { intro: '', questions };
  }

  return {
    intro: introLines.join('\n').trim(),
    questions
  };
}

function splitGlobalAnswerSection(markdown: string) {
  const lines = markdown.split('\n');
  const answerIndex = lines.findIndex((line) => /^##\s*参考答案/.test(line.trim()));
  if (answerIndex < 0) {
    return null;
  }
  return {
    questions: lines.slice(0, answerIndex).join('\n').trim(),
    answers: lines.slice(answerIndex + 1).join('\n').trim()
  };
}

function splitInlineAnswerSections(markdown: string) {
  const lines = removeTopTitle(markdown).split('\n');
  const questionLines: string[] = [];
  const answerSections: string[] = [];
  let currentAnswer: string[] = [];
  let collectingAnswer = false;
  let currentQuestionHeading = '';

  const flushAnswer = () => {
    if (collectingAnswer && currentAnswer.length) {
      answerSections.push(currentAnswer.join('\n').trim());
    }
    currentAnswer = [];
    collectingAnswer = false;
  };

  lines.forEach((line) => {
    if (isQuestionHeading(line)) {
      flushAnswer();
      currentQuestionHeading = line.replace(/^#{2,3}\s+/, '').trim();
      questionLines.push(line);
      return;
    }

    if (isAnswerMarker(line)) {
      flushAnswer();
      collectingAnswer = true;
      currentAnswer = [`### ${currentQuestionHeading || '参考答案'}`];
      const answerLead = line.replace(/^\s*\*\*参考答案[:：]?\*\*\s*[:：]?/, '').trim();
      if (answerLead) {
        currentAnswer.push(answerLead);
      }
      return;
    }

    if (collectingAnswer) {
      currentAnswer.push(line);
    } else {
      questionLines.push(line);
    }
  });

  flushAnswer();

  return {
    questions: questionLines.join('\n').trim(),
    answers: answerSections.join('\n\n---\n\n').trim()
  };
}

function buildPrintableCheckSheet(checkContent: string): PrintableCheckSheet {
  const globalSplit = splitGlobalAnswerSection(checkContent);
  const split = globalSplit ?? splitInlineAnswerSections(checkContent);
  const { intro, questions } = splitQuestions(split.questions);

  return {
    intro,
    questions,
    answers: removeTopTitle(split.answers)
  };
}

function PrintSheet({ chapter }: { chapter: ChapterContent }) {
  const printable = buildPrintableCheckSheet(chapter.content.check);

  return (
    <article className="print-sheet" aria-hidden="true">
      <header className="print-header">
        <div className="eyebrow">Day {String(chapter.no).padStart(2, '0')}</div>
        <h1>{chapter.title} 自测卷</h1>
        <p>先独立完成题目页，再单独翻到答案页对照复盘。</p>
        <div className="print-meta">
          <span>{chapter.priority}</span>
          <span>{chapter.hours}</span>
        </div>
      </header>

      <section className="print-section print-check">
        <div className="print-module-tag">本页：自测题</div>
        <h2>题目</h2>
        {printable.intro ? (
          <div className="print-intro">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{printable.intro}</ReactMarkdown>
          </div>
        ) : null}

        {printable.questions.map((question, index) => (
          <section className="print-question" key={question.id}>
            <div className="print-question-index">第 {index + 1} 题</div>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{question.content}</ReactMarkdown>
            <div className="print-answer-space">
              <strong>作答区</strong>
            </div>
          </section>
        ))}
      </section>

      {printable.answers ? (
        <section className="print-section print-answers">
          <div className="print-module-tag">单独页：参考答案</div>
          <h2>参考答案</h2>
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{printable.answers}</ReactMarkdown>
        </section>
      ) : null}
    </article>
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

function Today({
  today,
  onOpenChapter,
  onSwitchView
}: {
  today: TodayPlan | null;
  onOpenChapter: (no: number, view?: ViewKey) => void;
  onSwitchView: (view: ViewKey) => void;
}) {
  return (
    <section className="dashboard">
      <header className="dashboard-hero coach-hero">
        <div>
          <div className="eyebrow">今日作战台</div>
          <h2>{today?.focus ?? '读取中'}</h2>
          <p>先回忆，再阅读；先输出，再打勾。</p>
        </div>
        <div className="coach-score">
          <strong>{today?.dueReviews.length ?? 0}</strong>
          <span>到期复习</span>
        </div>
      </header>

      <div className="dashboard-grid coach-grid">
        <section className="panel">
          <h3>今日时间块</h3>
          {(today?.blocks ?? []).map((item) => (
            <div className="check-row" key={item}>
              <CheckCircle2 size={16} />
              <span>{item}</span>
            </div>
          ))}
        </section>

        <section className="panel">
          <h3>完成标准</h3>
          {(today?.acceptance ?? []).map((item) => (
            <div className="check-row" key={item}>
              <ClipboardCheck size={16} />
              <span>{item}</span>
            </div>
          ))}
        </section>

        <section className="panel">
          <h3>当前章节</h3>
          {today?.currentChapter ? (
            <button className="task-row" onClick={() => onOpenChapter(today.currentChapter!.no, 'study')}>
              <BookOpen size={18} />
              <span>{String(today.currentChapter.no).padStart(2, '0')} {today.currentChapter.title}</span>
            </button>
          ) : <p className="empty-text">当前没有待学习章节。</p>}
        </section>

        <section className="panel">
          <h3>主动回忆</h3>
          <button className="task-row" onClick={() => onSwitchView('cards')}>
            <TimerReset size={18} />
            <span>{today?.dueCards.length ?? 0} 张复习卡待处理</span>
          </button>
        </section>
      </div>
    </section>
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

function Cards({
  cards,
  answer,
  setAnswer,
  onAnswer,
  busy
}: {
  cards: ReviewCard[];
  answer: string;
  setAnswer: (value: string) => void;
  onAnswer: (cardId: number, remembered: boolean) => void;
  busy: boolean;
}) {
  return (
    <section className="reviews-page">
      <header className="page-title">
        <div>
          <div className="eyebrow">主动回忆</div>
          <h2>复习卡片</h2>
          <p>先凭记忆写，再判断是否记住；答错会自动进入明日加练。</p>
        </div>
      </header>
      <div className="review-list">
        {cards.length ? cards.map((card) => (
          <div className="review-item card-item" key={card.id}>
            <div>
              <span className="review-date">{card.dueDate} · {card.type}</span>
              <h3>{String(card.chapterNo).padStart(2, '0')} {card.chapterTitle}</h3>
              <p>{card.prompt}</p>
              <details>
                <summary>参考要点</summary>
                <p>{card.expected}</p>
              </details>
              <textarea value={answer} onChange={(event) => setAnswer(event.target.value)} placeholder="先写你的回忆答案，再点下面按钮" />
            </div>
            <div className="button-row">
              <button disabled={busy} onClick={() => onAnswer(card.id, true)}>记住了</button>
              <button disabled={busy} onClick={() => onAnswer(card.id, false)}>需加练</button>
            </div>
          </div>
        )) : <div className="panel"><p className="empty-text">今天没有到期复习卡。</p></div>}
      </div>
    </section>
  );
}

function PortfolioView({
  portfolio,
  chapter,
  body,
  setBody,
  onSave,
  busy
}: {
  portfolio: Portfolio | null;
  chapter: ChapterContent | null;
  body: string;
  setBody: (value: string) => void;
  onSave: () => void;
  busy: boolean;
}) {
  return (
    <section className="reviews-page">
      <header className="page-title">
        <div>
          <div className="eyebrow">转 Java 岗作品线</div>
          <h2>博客 API 作品集</h2>
          <p>把每个里程碑沉淀成面试能讲、简历能写、代码能跑的证据。</p>
        </div>
      </header>

      <div className="dashboard-grid">
        <section className="panel">
          <h3>里程碑</h3>
          {(portfolio?.milestones ?? []).map((item) => (
            <div className="check-row" key={item}>
              <ListChecks size={16} />
              <span>{item}</span>
            </div>
          ))}
        </section>

        <section className="panel">
          <h3>记录当前章节证据</h3>
          <p className="helper-text">{chapter ? `当前：${String(chapter.no).padStart(2, '0')} ${chapter.title}` : '先打开一个章节，再记录证据。'}</p>
          <textarea value={body} onChange={(event) => setBody(event.target.value)} placeholder="代码链接、接口截图、测试命令、README 片段、2 分钟项目表达..." />
          <button className="primary-action" disabled={busy || !chapter || !body.trim()} onClick={onSave}>
            <Save size={17} />
            保存到作品集
          </button>
        </section>

        <section className="panel wide">
          <h3>已保存证据（{portfolio?.totalEvidence ?? 0}）</h3>
          <div className="evidence-list">
            {(portfolio?.evidence ?? []).map((item) => (
              <div key={item.id} className="evidence-item">
                <span>{item.createdAt.slice(0, 10)} · {item.milestone}</span>
                <p>{item.body}</p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </section>
  );
}

const FREQUENCY_LABEL: Record<string, string> = {
  HIGH: '⭐⭐⭐ 必考',
  MEDIUM: '⭐⭐ 高频',
  LOW: '⭐ 加分'
};

const DIFFICULTY_LABEL: Record<string, string> = {
  L1: 'L1 必答',
  L2: 'L2 项目',
  L3: 'L3 进阶'
};

function InterviewView({ onOpenChapter }: { onOpenChapter: (no: number, view?: ViewKey) => void }) {
  const [categories, setCategories] = useState<InterviewCategory[]>([]);
  const [questions, setQuestions] = useState<InterviewQuestionItem[]>([]);
  const [activeCategory, setActiveCategory] = useState<string>('');
  const [frequencyFilter, setFrequencyFilter] = useState<string>('');
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<InterviewQuestionDetail | null>(null);
  const [answer, setAnswer] = useState('');
  const [revealAnswer, setRevealAnswer] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function loadCategories() {
    try {
      const next = await api<InterviewCategory[]>('/api/interview/categories');
      setCategories(next);
      if (!activeCategory && next.length > 0) {
        setActiveCategory(next[0].category);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载分类失败');
    }
  }

  async function loadQuestions() {
    try {
      const params = new URLSearchParams();
      if (activeCategory) params.set('category', activeCategory);
      if (frequencyFilter) params.set('frequency', frequencyFilter);
      if (keyword.trim()) params.set('q', keyword.trim());
      const next = await api<InterviewQuestionItem[]>(`/api/interview/questions?${params.toString()}`);
      setQuestions(next);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载题目失败');
    }
  }

  async function loadDetail(id: number) {
    setSelectedId(id);
    setAnswer('');
    setRevealAnswer(false);
    try {
      const next = await api<InterviewQuestionDetail>(`/api/interview/questions/${id}`);
      setDetail(next);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载详情失败');
    }
  }

  async function submitAttempt(selfAssessed: 'mastered' | 'practiced' | 'again') {
    if (!detail || !answer.trim()) return;
    setBusy(true);
    setError('');
    try {
      await api('/api/interview/attempts', {
        method: 'POST',
        body: JSON.stringify({ questionId: detail.id, answer: answer.trim(), selfAssessed })
      });
      await Promise.all([loadCategories(), loadQuestions(), loadDetail(detail.id)]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '提交失败');
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    loadCategories().catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    loadQuestions().catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeCategory, frequencyFilter, keyword]);

  const totalQuestions = categories.reduce((sum, c) => sum + c.total, 0);
  const totalAttempted = categories.reduce((sum, c) => sum + c.attempted, 0);
  const totalMastered = categories.reduce((sum, c) => sum + c.mastered, 0);

  return (
    <section className="dashboard">
      <header className="dashboard-hero">
        <div>
          <div className="eyebrow">面试刷题</div>
          <h2>Java 后端高频题库</h2>
          <p>结构化整理 interview-bank + 各章 Q5，按高频 / 项目 / 进阶分层。</p>
        </div>
        <div className="coach-score">
          <strong>{totalMastered}/{totalQuestions}</strong>
          <span>已掌握 / 全部</span>
        </div>
      </header>

      <div className="metric-grid">
        <div className="metric"><span>题库总数</span><strong>{totalQuestions}</strong></div>
        <div className="metric"><span>已练题数</span><strong>{totalAttempted}</strong></div>
        <div className="metric"><span>已掌握</span><strong>{totalMastered}</strong></div>
      </div>

      {error ? <div className="toast"><AlertCircle size={16} />{error}</div> : null}

      <div className="dashboard-grid" style={{ gridTemplateColumns: '220px 1fr 1fr' }}>
        <section className="panel">
          <h3>分类</h3>
          {categories.map((c) => (
            <button
              key={c.category}
              className={`task-row ${activeCategory === c.category ? 'selected' : ''}`}
              onClick={() => setActiveCategory(c.category)}
            >
              <span>{c.label}</span>
              <b>{c.mastered}/{c.total}</b>
            </button>
          ))}
        </section>

        <section className="panel">
          <h3>题目列表</h3>
          <div className="search-box" style={{ marginBottom: 8 }}>
            <Search size={16} />
            <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="搜索题干关键词" />
          </div>
          <div className="button-row" style={{ marginBottom: 8 }}>
            {['', 'HIGH', 'MEDIUM', 'LOW'].map((f) => (
              <button
                key={f || 'all'}
                className={frequencyFilter === f ? 'active' : ''}
                onClick={() => setFrequencyFilter(f)}
              >
                {f === '' ? '全部' : FREQUENCY_LABEL[f].split(' ')[0]}
              </button>
            ))}
          </div>
          <div className="evidence-list">
            {questions.map((q) => (
              <button
                key={q.id}
                className={`evidence-item interview-question-row ${selectedId === q.id ? 'selected' : ''} ${q.mastered ? 'mastered' : ''}`}
                onClick={() => loadDetail(q.id)}
              >
                <div className="interview-tags">
                  <span className="priority l1">{FREQUENCY_LABEL[q.frequency]}</span>
                  <span className="priority l2">{DIFFICULTY_LABEL[q.difficulty]}</span>
                  {q.attempted ? <span className="priority l3">已练</span> : null}
                  {q.relatedChapterNo ? <span>章节 {String(q.relatedChapterNo).padStart(2, '0')}</span> : null}
                </div>
                <p>{q.prompt}</p>
              </button>
            ))}
            {questions.length === 0 ? <p className="empty-text">没有符合条件的题目。</p> : null}
          </div>
        </section>

        <section className="panel">
          <h3>答题</h3>
          {detail ? (
            <>
              <div className="interview-tags">
                <span className="priority l1">{FREQUENCY_LABEL[detail.frequency]}</span>
                <span className="priority l2">{DIFFICULTY_LABEL[detail.difficulty]}</span>
                {detail.relatedChapterNo ? (
                  <span>关联章节 {String(detail.relatedChapterNo).padStart(2, '0')}</span>
                ) : null}
              </div>
              {detail.relatedChapterNo ? (
                <button className="task-row" onClick={() => onOpenChapter(detail.relatedChapterNo!, 'study')}>
                  <BookOpen size={16} />
                  <span>去章节 {String(detail.relatedChapterNo).padStart(2, '0')} {detail.relatedChapterTitle ?? ''}</span>
                </button>
              ) : null}
              <article className="markdown-body">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{detail.prompt}</ReactMarkdown>
              </article>
              <textarea
                value={answer}
                onChange={(e) => setAnswer(e.target.value)}
                placeholder="先用自己的话写一遍，再看参考答案"
                rows={6}
              />
              <div className="button-row">
                <button disabled={busy || !answer.trim()} onClick={() => submitAttempt('mastered')}>掌握</button>
                <button disabled={busy || !answer.trim()} onClick={() => submitAttempt('practiced')}>记录</button>
                <button disabled={busy || !answer.trim()} onClick={() => submitAttempt('again')}>需再练</button>
                <button onClick={() => setRevealAnswer(!revealAnswer)}>
                  <FileText size={16} />
                  {revealAnswer ? '隐藏参考' : '看参考答案'}
                </button>
              </div>
              {revealAnswer ? (
                <article className="markdown-body">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{detail.referenceAnswer}</ReactMarkdown>
                  {detail.followUp ? (
                    <blockquote>🔥 追问：{detail.followUp}</blockquote>
                  ) : null}
                </article>
              ) : null}
              {detail.recentAttempts.length > 0 ? (
                <details>
                  <summary>最近 {detail.recentAttempts.length} 次作答</summary>
                  {detail.recentAttempts.map((a) => (
                    <div key={a.id} className="evidence-item">
                      <span>{a.createdAt.slice(0, 16).replace('T', ' ')} · {a.selfAssessed}</span>
                      <p>{a.answer}</p>
                    </div>
                  ))}
                </details>
              ) : null}
            </>
          ) : (
            <p className="empty-text">从左侧选一道题开始作答。</p>
          )}
        </section>
      </div>
    </section>
  );
}

export default App;
