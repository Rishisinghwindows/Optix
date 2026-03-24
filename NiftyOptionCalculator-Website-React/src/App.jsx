import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom'
import { initAnalytics, trackPageView, logScreenView } from './services/analytics'
import Navbar from './components/Navbar'
import Hero from './components/Hero'
import Calculator from './components/Calculator'
import Features from './components/Features'
import Learn from './components/Learn'
import AIInsightsShowcase from './components/AIInsightsShowcase'
import Screenshots from './components/Screenshots'
import Indices from './components/Indices'
import Pricing from './components/Pricing'
import Download from './components/Download'
import Footer from './components/Footer'
import FloatingChatbot from './components/FloatingChatbot'
import GamesBanner from './components/GamesBanner'
import Support from './components/Support'

// App Pages
import AppLayout from './components/app/AppLayout'
import OptionChain from './components/app/OptionChain'
import PaperTrading from './components/app/PaperTrading'
import AIInsights from './components/app/AIInsights'
import Education from './components/app/Education'
import Quiz from './components/app/Quiz'
import CalculatorApp from './components/app/CalculatorApp'
import Backtest from './components/app/Backtest'
import IPODashboard from './components/app/IPODashboard'
import Alerts from './components/app/Alerts'
import OptionScreener from './components/app/OptionScreener'
import PnLSimulator from './components/app/PnLSimulator'
import TradeJournal from './components/app/TradeJournal'
import LoginPage from './components/app/LoginPage'
import GamesHub from './components/games/GamesHub'

// Legal Pages
import PrivacyPolicy from './components/legal/PrivacyPolicy'
import TermsOfService from './components/legal/TermsOfService'
import Disclaimer from './components/legal/Disclaimer'

// Auth
import { AuthProvider } from './context/AuthContext'

// Ads
import { AdProvider } from './context/AdContext'
import { AdBanner, AnchorAd } from './components/ads'
import { ADS_CONFIG } from './config/adsConfig'

/**
 * Invisible component that wires React Router navigation to analytics.
 * Placed inside <BrowserRouter> so it can access useLocation().
 * - On mount: bootstraps both custom + Firebase analytics (initAnalytics).
 * - On every route change: sends a custom visit event and a Firebase screen_view.
 */
function AnalyticsTracker() {
  const location = useLocation();

  useEffect(() => {
    initAnalytics();
  }, []);

  useEffect(() => {
    // Dual-track: custom backend visit + Firebase screen_view
    trackPageView(location.pathname, document.title);
    logScreenView(location.pathname);
  }, [location]);

  return null; // renders nothing — purely side-effect driven
}

function LandingPage() {
  return (
    <>
      <Navbar />
      <Hero />
      <AIInsightsShowcase />
      <Calculator />
      <Features />
      <Learn />
      <GamesBanner />
      <Screenshots />
      <Indices />
      <Pricing />
      <Download />

      {/* Pre-Footer Ad */}
      <div className="container">
        <AdBanner
          slot={ADS_CONFIG.adUnits.preFooterRectangle}
          placement="preFooterRectangle"
          className="ad-pre-footer"
        />
      </div>

      <Footer />
      <FloatingChatbot />

      {/* Anchor Ad (Sticky Bottom) */}
      <AnchorAd slot={ADS_CONFIG.adUnits.anchorBottom} />
    </>
  )
}

function App() {
  return (
    <AuthProvider>
      <AdProvider>
        <BrowserRouter>
        <AnalyticsTracker />
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/games" element={<GamesHub />} />
          <Route path="/support" element={<><Navbar /><Support /><Footer /></>} />
          <Route path="/legal/privacy" element={<><Navbar /><PrivacyPolicy /><Footer /></>} />
          <Route path="/legal/terms" element={<><Navbar /><TermsOfService /><Footer /></>} />
          <Route path="/legal/disclaimer" element={<><Navbar /><Disclaimer /><Footer /></>} />
          <Route path="/app" element={<AppLayout />}>
            <Route index element={<OptionChain />} />
            <Route path="chain" element={<OptionChain />} />
            <Route path="calculator" element={<CalculatorApp />} />
            <Route path="paper-trading" element={<PaperTrading />} />
            <Route path="ai-insights" element={<AIInsights />} />
            <Route path="education" element={<Education />} />
            <Route path="quiz" element={<Quiz />} />
            <Route path="backtest" element={<Backtest />} />
            <Route path="ipo" element={<IPODashboard />} />
            <Route path="alerts" element={<Alerts />} />
            <Route path="screener" element={<OptionScreener />} />
            <Route path="simulator" element={<PnLSimulator />} />
            <Route path="journal" element={<TradeJournal />} />
          </Route>
        </Routes>
        </BrowserRouter>
      </AdProvider>
    </AuthProvider>
  )
}

export default App
