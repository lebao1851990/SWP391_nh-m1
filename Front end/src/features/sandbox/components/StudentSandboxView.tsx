'use client';

import React, { useState } from 'react';
import { useExecuteSandbox } from '../hooks/use-sandbox';
import { SandboxResult } from '../api/sandbox.api';
import { 
  Play, 
  RotateCcw, 
  Terminal, 
  CheckCircle2,
  Clock, 
  Cpu, 
  ShieldCheck, 
  AlertCircle 
} from 'lucide-react';

const DEFAULT_JAVA_CODE = `public class Main {
    public static void main(String[] args) {
        System.out.println("Xin chào! Mã nguồn đang chạy trong Docker Sandbox của CodeAssess.");
        int sum = 0;
        for (int i = 1; i <= 10; i++) {
            sum += i;
        }
        System.out.println("Tổng từ 1 đến 10 là: " + sum);
    }
}
`;

export function StudentSandboxView() {
  const [sourceCode, setSourceCode] = useState<string>(DEFAULT_JAVA_CODE);
  const [activeTab, setActiveTab] = useState<'stdout' | 'compiler' | 'stderr'>('stdout');
  const executeMutation = useExecuteSandbox();
  const [lastResult, setLastResult] = useState<SandboxResult | null>(null);

  const handleRun = async () => {
    if (!sourceCode.trim()) return;
    try {
      const res = await executeMutation.mutateAsync(sourceCode);
      setLastResult(res);
      if (res.status === 'COMPILE_ERROR' && res.compilerOutput) {
        setActiveTab('compiler');
      } else if (res.status === 'RUNTIME_ERROR' && res.stderr) {
        setActiveTab('stderr');
      } else {
        setActiveTab('stdout');
      }
    } catch (err) {
      console.error('Execution failed:', err);
    }
  };

  const handleReset = () => {
    setSourceCode(DEFAULT_JAVA_CODE);
    setLastResult(null);
  };

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col gap-space-lg">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="p-1.5 rounded-lg bg-primary/10 text-primary">
              <Terminal className="w-5 h-5 text-primary" />
            </span>
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">
              Sandbox Thực thi Mã nguồn
            </h1>
          </div>
          <p className="text-xs text-on-surface-variant">
            Biên dịch và chạy thử Java trong container Docker cô lập, an toàn. Không lưu điểm hoặc kích hoạt kiểm tra đạo văn.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleReset}
            disabled={executeMutation.isPending}
            className="px-3 py-2 bg-surface-container hover:bg-surface-container-high text-on-surface border border-outline-variant/40 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Khôi phục mẫu</span>
          </button>
          <button
            onClick={handleRun}
            disabled={executeMutation.isPending || !sourceCode.trim()}
            className="px-4 py-2 bg-primary hover:bg-primary/90 text-on-primary rounded-lg text-xs font-semibold flex items-center gap-2 shadow-xs transition-colors cursor-pointer disabled:opacity-50"
          >
            {executeMutation.isPending ? (
              <>
                <div className="w-3.5 h-3.5 rounded-full border-2 border-on-primary border-t-transparent animate-spin" />
                <span>Đang thực thi trong Docker...</span>
              </>
            ) : (
              <>
                <Play className="w-3.5 h-3.5 fill-current" />
                <span>Chạy mã nguồn (Run)</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Editor & Output Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg">
        {/* Code Editor Panel */}
        <div className="lg:col-span-7 flex flex-col bg-surface-container-lowest rounded-xl border border-outline-variant/40 overflow-hidden shadow-xs">
          <div className="flex items-center justify-between px-space-md py-2.5 bg-surface-container-low border-b border-outline-variant/30 text-xs font-mono">
            <div className="flex items-center gap-2">
              <Cpu className="w-4 h-4 text-primary" />
              <span className="font-semibold text-on-surface">Main.java</span>
            </div>
            <span className="text-outline text-[11px]">Java 17 OpenJDK</span>
          </div>
          <textarea
            value={sourceCode}
            onChange={(e) => setSourceCode(e.target.value)}
            spellCheck={false}
            rows={18}
            className="w-full p-space-md bg-slate-950 text-slate-100 font-mono text-xs leading-relaxed focus:outline-hidden resize-none overflow-y-auto"
          />
        </div>

        {/* Output Panel */}
        <div className="lg:col-span-5 flex flex-col bg-surface-container-lowest rounded-xl border border-outline-variant/40 overflow-hidden shadow-xs">
          {/* Output Header */}
          <div className="flex items-center justify-between px-space-md py-2 bg-surface-container-low border-b border-outline-variant/30">
            <div className="flex items-center gap-1.5">
              <Terminal className="w-4 h-4 text-outline" />
              <span className="text-xs font-semibold text-on-surface">Kết quả thực thi</span>
            </div>
            {lastResult && (
              <span className={`text-xs px-2 py-0.5 rounded-full font-semibold border flex items-center gap-1 ${
                lastResult.status === 'SUCCESS' 
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200' 
                  : lastResult.status === 'COMPILE_ERROR'
                  ? 'bg-rose-50 text-rose-700 border-rose-200'
                  : 'bg-amber-50 text-amber-700 border-amber-200'
              }`}>
                {lastResult.status === 'SUCCESS' ? (
                  <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                ) : (
                  <AlertCircle className="w-3 h-3 text-rose-600" />
                )}
                <span>{lastResult.status}</span>
              </span>
            )}
          </div>

          {/* Result Metadata Stats */}
          {lastResult && (
            <div className="flex items-center justify-between px-space-md py-2 bg-surface-container border-b border-outline-variant/30 text-[11px] text-on-surface-variant font-mono">
              <div className="flex items-center gap-1">
                <Clock className="w-3 h-3 text-outline" />
                <span>Thời gian: {lastResult.executionTimeMs} ms</span>
              </div>
              <div className="flex items-center gap-1">
                <span>Exit code: {lastResult.exitCode ?? '—'}</span>
              </div>
            </div>
          )}

          {/* Output Tabs */}
          <div className="flex items-center border-b border-outline-variant/30 bg-surface-container-low/50 px-2 pt-1 gap-1">
            <button
              onClick={() => setActiveTab('stdout')}
              className={`px-3 py-1.5 text-xs font-medium rounded-t transition-colors ${
                activeTab === 'stdout'
                  ? 'bg-surface-container-lowest text-primary border-t-2 border-primary font-semibold'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              Standard Output (stdout)
            </button>
            <button
              onClick={() => setActiveTab('compiler')}
              className={`px-3 py-1.5 text-xs font-medium rounded-t transition-colors ${
                activeTab === 'compiler'
                  ? 'bg-surface-container-lowest text-primary border-t-2 border-primary font-semibold'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              Compiler Output
            </button>
            <button
              onClick={() => setActiveTab('stderr')}
              className={`px-3 py-1.5 text-xs font-medium rounded-t transition-colors ${
                activeTab === 'stderr'
                  ? 'bg-surface-container-lowest text-primary border-t-2 border-primary font-semibold'
                  : 'text-on-surface-variant hover:text-on-surface'
              }`}
            >
              Standard Error (stderr)
            </button>
          </div>

          {/* Console Output Screen */}
          <div className="flex-1 min-h-[300px] p-space-md bg-slate-950 font-mono text-xs overflow-y-auto">
            {executeMutation.isPending ? (
              <div className="flex flex-col items-center justify-center h-full text-slate-400 gap-2 py-12">
                <div className="w-6 h-6 rounded-full border-2 border-primary border-t-transparent animate-spin" />
                <span>Đang khởi động Docker Sandbox và thực thi mã nguồn...</span>
              </div>
            ) : !lastResult ? (
              <div className="flex flex-col items-center justify-center h-full text-slate-500 gap-2 py-12 text-center">
                <Terminal className="w-8 h-8 opacity-40" />
                <span>Bấm &quot;Chạy mã nguồn&quot; để biên dịch và chạy trong Docker container.</span>
              </div>
            ) : (
              <div>
                {activeTab === 'stdout' && (
                  <pre className="text-emerald-400 whitespace-pre-wrap">
                    {lastResult.stdout || '(Không có dữ liệu stdout)'}
                  </pre>
                )}
                {activeTab === 'compiler' && (
                  <pre className={`whitespace-pre-wrap ${lastResult.compilerOutput ? 'text-amber-300' : 'text-slate-400'}`}>
                    {lastResult.compilerOutput || '(Biên dịch thành công không có cảnh báo)'}
                  </pre>
                )}
                {activeTab === 'stderr' && (
                  <pre className={`whitespace-pre-wrap ${lastResult.stderr ? 'text-rose-400' : 'text-slate-400'}`}>
                    {lastResult.stderr || '(Không có lỗi runtime)'}
                  </pre>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Security & Sandbox Info */}
      <div className="rounded-xl border border-outline-variant/30 bg-surface-container-low/40 p-space-md flex items-start gap-space-sm">
        <ShieldCheck className="w-5 h-5 text-primary shrink-0 mt-0.5" />
        <div className="flex flex-col gap-1 text-xs text-on-surface-variant">
          <span className="font-semibold text-on-surface">Bảo mật Sandbox Docker:</span>
          <span>
            Mã nguồn của bạn được cô lập trong container Linux với giới hạn bộ nhớ (RAM 256MB), thời gian CPU (5s), và không có kết nối mạng ngoài. Bạn có thể tự do thử nghiệm các giải thuật trước khi nộp bài.
          </span>
        </div>
      </div>
    </div>
  );
}

