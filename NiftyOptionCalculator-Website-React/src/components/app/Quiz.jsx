import React, { useState } from 'react'

const quizzes = [
  {
    id: 'basics',
    title: 'Options Basics',
    questions: 5,
    difficulty: 'Beginner',
    icon: '📖'
  },
  {
    id: 'greeks',
    title: 'Understanding Greeks',
    questions: 5,
    difficulty: 'Intermediate',
    icon: '🔢'
  },
  {
    id: 'strategies',
    title: 'Trading Strategies',
    questions: 5,
    difficulty: 'Advanced',
    icon: '📊'
  }
]

const quizQuestions = {
  basics: [
    {
      question: 'What does a Call option give the buyer?',
      options: [
        'The right to sell at the strike price',
        'The right to buy at the strike price',
        'The obligation to buy at the strike price',
        'The obligation to sell at the strike price'
      ],
      correct: 1,
      explanation: 'A Call option gives the buyer the RIGHT (not obligation) to BUY the underlying at the strike price.'
    },
    {
      question: 'What is the premium of an option?',
      options: [
        'The strike price',
        'The price paid to purchase the option',
        'The expiry date',
        'The spot price'
      ],
      correct: 1,
      explanation: 'Premium is the price paid by the buyer to the seller for the option contract.'
    },
    {
      question: 'If NIFTY is at 24,000, a 23,800 CE is:',
      options: [
        'Out of the Money (OTM)',
        'At the Money (ATM)',
        'In the Money (ITM)',
        'Cannot be determined'
      ],
      correct: 2,
      explanation: 'For a Call option, when Strike (23,800) < Spot (24,000), the option is In the Money.'
    },
    {
      question: 'What happens to an option at expiry if it\'s OTM?',
      options: [
        'It gets automatically exercised',
        'It expires worthless',
        'It converts to a futures contract',
        'It rolls over to next expiry'
      ],
      correct: 1,
      explanation: 'OTM options have no intrinsic value and expire worthless at expiry.'
    },
    {
      question: 'Which option benefits when the market goes down?',
      options: [
        'Long Call',
        'Short Call',
        'Long Put',
        'All of the above'
      ],
      correct: 2,
      explanation: 'A Long Put profits when the underlying price falls below the strike price.'
    }
  ],
  greeks: [
    {
      question: 'What does Delta measure?',
      options: [
        'Time decay of the option',
        'Price sensitivity to underlying movement',
        'Volatility sensitivity',
        'Interest rate sensitivity'
      ],
      correct: 1,
      explanation: 'Delta measures how much an option\'s price changes for every ₹1 change in the underlying.'
    },
    {
      question: 'A Call option with Delta 0.6 means:',
      options: [
        'Option price increases ₹0.60 when underlying increases ₹1',
        'Option has 60% time value',
        'Option loses ₹0.60 per day',
        'Option has 60% IV'
      ],
      correct: 0,
      explanation: 'Delta 0.6 means the option price changes by ₹0.60 for every ₹1 change in the underlying.'
    },
    {
      question: 'Theta is negative for option buyers because:',
      options: [
        'Option price increases over time',
        'Options lose value as time passes',
        'Volatility decreases over time',
        'Interest rates fall over time'
      ],
      correct: 1,
      explanation: 'Theta represents time decay - options lose value as expiry approaches, hurting buyers.'
    },
    {
      question: 'Which Greek is highest for ATM options?',
      options: [
        'Delta only',
        'Theta and Gamma',
        'Vega only',
        'Rho only'
      ],
      correct: 1,
      explanation: 'ATM options have the highest Gamma (rate of delta change) and Theta (time decay).'
    },
    {
      question: 'Vega measures sensitivity to:',
      options: [
        'Underlying price',
        'Time to expiry',
        'Implied Volatility',
        'Interest rate'
      ],
      correct: 2,
      explanation: 'Vega measures how much an option price changes for every 1% change in Implied Volatility.'
    }
  ],
  strategies: [
    {
      question: 'A Bull Call Spread involves:',
      options: [
        'Buying a Put and selling a Call',
        'Buying a lower strike Call and selling a higher strike Call',
        'Buying and selling Calls of the same strike',
        'Selling two Calls at different strikes'
      ],
      correct: 1,
      explanation: 'Bull Call Spread = Buy lower strike Call + Sell higher strike Call (same expiry).'
    },
    {
      question: 'Iron Condor is a:',
      options: [
        'Directional bullish strategy',
        'Directional bearish strategy',
        'Non-directional, range-bound strategy',
        'High volatility strategy'
      ],
      correct: 2,
      explanation: 'Iron Condor profits when the underlying stays within a range - it\'s non-directional.'
    },
    {
      question: 'When should you use a Long Straddle?',
      options: [
        'When you expect low volatility',
        'When you expect a big move but unsure of direction',
        'When you expect no movement',
        'When you want to collect premium'
      ],
      correct: 1,
      explanation: 'Long Straddle profits from large moves in either direction - buy ATM Call + ATM Put.'
    },
    {
      question: 'Maximum loss in a Covered Call is:',
      options: [
        'Unlimited',
        'The premium received',
        'Stock price minus premium received',
        'Strike price minus premium'
      ],
      correct: 2,
      explanation: 'In Covered Call, max loss occurs if stock goes to zero = Entry price - Premium received.'
    },
    {
      question: 'Which strategy benefits from time decay (positive theta)?',
      options: [
        'Long Call',
        'Long Put',
        'Short Straddle',
        'Long Straddle'
      ],
      correct: 2,
      explanation: 'Option sellers (like Short Straddle) have positive theta and benefit from time decay.'
    }
  ]
}

function Quiz() {
  const [selectedQuiz, setSelectedQuiz] = useState(null)
  const [currentQuestion, setCurrentQuestion] = useState(0)
  const [selectedAnswer, setSelectedAnswer] = useState(null)
  const [hasChecked, setHasChecked] = useState(false)
  const [score, setScore] = useState(0)
  const [showResults, setShowResults] = useState(false)
  const [completedQuizzes, setCompletedQuizzes] = useState({})

  const startQuiz = (quizId) => {
    setSelectedQuiz(quizId)
    setCurrentQuestion(0)
    setSelectedAnswer(null)
    setHasChecked(false)
    setScore(0)
    setShowResults(false)
  }

  const checkAnswer = () => {
    if (selectedAnswer === null) return

    setHasChecked(true)
    if (selectedAnswer === quizQuestions[selectedQuiz][currentQuestion].correct) {
      setScore(prev => prev + 1)
    }
  }

  const nextQuestion = () => {
    if (currentQuestion < quizQuestions[selectedQuiz].length - 1) {
      setCurrentQuestion(prev => prev + 1)
      setSelectedAnswer(null)
      setHasChecked(false)
    } else {
      setShowResults(true)
      setCompletedQuizzes(prev => ({
        ...prev,
        [selectedQuiz]: score + (selectedAnswer === quizQuestions[selectedQuiz][currentQuestion].correct ? 1 : 0)
      }))
    }
  }

  const exitQuiz = () => {
    setSelectedQuiz(null)
    setShowResults(false)
  }

  if (selectedQuiz && showResults) {
    const totalQuestions = quizQuestions[selectedQuiz].length
    const percentage = Math.round((score / totalQuestions) * 100)

    return (
      <div className="quiz-page">
        <div className="quiz-results">
          <div className="results-icon">
            {percentage === 100 ? '🏆' : percentage >= 60 ? '⭐' : '📚'}
          </div>
          <h2>Quiz Complete!</h2>
          <div className="results-score">
            <span className="score-number">{score}</span>
            <span className="score-total">/{totalQuestions}</span>
          </div>
          <div className={`results-percentage ${percentage >= 60 ? 'pass' : 'fail'}`}>
            {percentage}%
          </div>
          <p className="results-message">
            {percentage === 100 ? 'Perfect score! You\'re an options expert!' :
             percentage >= 80 ? 'Great job! You have a solid understanding.' :
             percentage >= 60 ? 'Good effort! Review the lessons to improve.' :
             'Keep learning! Review the education section and try again.'}
          </p>
          <div className="results-actions">
            <button className="btn-secondary" onClick={() => startQuiz(selectedQuiz)}>
              Retry Quiz
            </button>
            <button className="btn-primary" onClick={exitQuiz}>
              Back to Quizzes
            </button>
          </div>
        </div>
      </div>
    )
  }

  if (selectedQuiz) {
    const questions = quizQuestions[selectedQuiz]
    const question = questions[currentQuestion]

    return (
      <div className="quiz-page">
        <div className="quiz-header">
          <button className="close-btn" onClick={exitQuiz}>✕</button>
          <div className="quiz-progress">
            <span>Question {currentQuestion + 1} of {questions.length}</span>
            <div className="progress-bar">
              <div
                className="progress-fill"
                style={{ width: `${((currentQuestion + 1) / questions.length) * 100}%` }}
              ></div>
            </div>
          </div>
          <div className="quiz-score">Score: {score}</div>
        </div>

        <div className="quiz-content">
          <h3 className="question-text">{question.question}</h3>

          <div className="options-list">
            {question.options.map((option, idx) => {
              let optionClass = 'option-btn'
              if (hasChecked) {
                if (idx === question.correct) {
                  optionClass += ' correct'
                } else if (idx === selectedAnswer) {
                  optionClass += ' incorrect'
                }
              } else if (idx === selectedAnswer) {
                optionClass += ' selected'
              }

              return (
                <button
                  key={idx}
                  className={optionClass}
                  onClick={() => !hasChecked && setSelectedAnswer(idx)}
                  disabled={hasChecked}
                >
                  <span className="option-letter">{String.fromCharCode(65 + idx)}</span>
                  <span className="option-text">{option}</span>
                  {hasChecked && idx === question.correct && <span className="check-icon">✓</span>}
                  {hasChecked && idx === selectedAnswer && idx !== question.correct && <span className="x-icon">✗</span>}
                </button>
              )
            })}
          </div>

          {hasChecked && (
            <div className="explanation-box">
              <h4>Explanation</h4>
              <p>{question.explanation}</p>
            </div>
          )}
        </div>

        <div className="quiz-footer">
          {!hasChecked ? (
            <button
              className="btn-primary"
              onClick={checkAnswer}
              disabled={selectedAnswer === null}
            >
              Check Answer
            </button>
          ) : (
            <button className="btn-primary" onClick={nextQuestion}>
              {currentQuestion === questions.length - 1 ? 'See Results' : 'Next Question'}
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="quiz-page">
      <div className="quiz-list-header">
        <h2>Test Your Knowledge</h2>
        <p>Take quizzes to reinforce your learning</p>
      </div>

      <div className="quiz-stats">
        <div className="stat-card">
          <span className="stat-number">{Object.keys(completedQuizzes).length}</span>
          <span className="stat-label">Completed</span>
        </div>
        <div className="stat-card">
          <span className="stat-number">{quizzes.length}</span>
          <span className="stat-label">Available</span>
        </div>
        <div className="stat-card">
          <span className="stat-number">
            {Object.keys(completedQuizzes).length > 0
              ? Math.round(Object.values(completedQuizzes).reduce((a, b) => a + b, 0) / Object.keys(completedQuizzes).length / 5 * 100)
              : '-'}
            {Object.keys(completedQuizzes).length > 0 && '%'}
          </span>
          <span className="stat-label">Avg Score</span>
        </div>
      </div>

      <div className="quizzes-list">
        {quizzes.map(quiz => (
          <div key={quiz.id} className="quiz-card" onClick={() => startQuiz(quiz.id)}>
            <div className="quiz-icon">{quiz.icon}</div>
            <div className="quiz-info">
              <h4>{quiz.title}</h4>
              <div className="quiz-meta">
                <span>{quiz.questions} questions</span>
                <span className={`difficulty ${quiz.difficulty.toLowerCase()}`}>{quiz.difficulty}</span>
              </div>
              {completedQuizzes[quiz.id] !== undefined && (
                <div className="quiz-completed">
                  ✓ Completed: {completedQuizzes[quiz.id]}/{quiz.questions}
                </div>
              )}
            </div>
            <div className="quiz-arrow">→</div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default Quiz
