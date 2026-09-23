'use client';

import React, { Component, ErrorInfo, ReactNode } from 'react';

interface Props {
  children?: ReactNode;
  activePath: string | null;
  onBack: () => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class PreviewErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('PreviewErrorBoundary caught an error:', error, errorInfo);
  }

  public componentDidUpdate(prevProps: Props) {
    if (prevProps.activePath !== this.props.activePath && this.state.hasError) {
      this.setState({ hasError: false, error: null });
    }
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center justify-center py-20 px-4 text-center">
          <div className="w-16 h-16 rounded-xl bg-error-container text-on-error-container flex items-center justify-center mb-4">
            <span className="material-symbols-outlined text-[32px]">warning</span>
          </div>
          <h2 className="font-headline-md text-headline-md text-on-surface mb-2">
            Component Render Error
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-6">
            The previewed component threw an error during rendering. This is often caused by missing required props, undefined context, or dynamic route parameters.
          </p>
          <div className="w-full max-w-2xl bg-surface-container-lowest border border-error/20 p-4 rounded-lg text-left overflow-x-auto mb-6">
            <code className="font-code-body text-[13px] text-error">
              {this.state.error?.toString()}
            </code>
          </div>
          <button 
            onClick={this.props.onBack}
            className="px-6 py-2.5 bg-primary text-on-primary rounded-lg font-label-md text-label-md font-semibold hover:bg-primary-hover transition-colors shadow-sm"
          >
            Back to Preview Menu
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
