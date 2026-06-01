use ratatui::{
    backend::CrosstermBackend,
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    widgets::{Block, Borders, Paragraph, Row, Table, List, ListItem},
    Terminal,
};
use crossterm::{
    event::{self, Event, KeyCode},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use std::io;
use std::time::{Duration, Instant};

pub struct TuiState {
    pub subdomain: String,
    pub relay: String,
    pub local_port: u16,
    pub active_connections: usize,
    pub requests_per_sec: f64,
}

pub struct TerminalUi {
    terminal: Terminal<CrosstermBackend<io::Stdout>>,
}

impl TerminalUi {
    pub fn new() -> io::Result<Self> {
        enable_raw_mode()?;
        let mut stdout = io::stdout();
        execute!(stdout, EnterAlternateScreen)?;
        let backend = CrosstermBackend::new(stdout);
        let terminal = Terminal::new(backend)?;
        Ok(Self { terminal })
    }

    pub fn draw(&mut self, state: &TuiState) -> io::Result<()> {
        self.terminal.draw(|f| {
            let chunks = Layout::default()
                .direction(Direction::Vertical)
                .margin(1)
                .constraints([
                    Constraint::Length(3),
                    Constraint::Length(6),
                    Constraint::Min(0),
                ].as_ref())
                .split(f.size());

            // Header
            let header = Paragraph::new("D-RAP Tunnel Active")
                .style(Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD))
                .block(Block::default().borders(Borders::ALL).title("Status"));
            f.render_widget(header, chunks[0]);

            // Stats
            let stats_text = format!(
                "Relay:      {}\nURL:        https://{}.{}\nLocal Port: {}\nActive:     {}\nTraffic:    {:.1} req/sec",
                state.relay, state.subdomain, state.relay, state.local_port, state.active_connections, state.requests_per_sec
            );
            let stats = Paragraph::new(stats_text)
                .block(Block::default().borders(Borders::ALL).title("Tunnel Info"));
            f.render_widget(stats, chunks[1]);

            // Events (Placeholder)
            let logs = List::new(vec![
                ListItem::new("Waiting for traffic...").style(Style::default().fg(Color::DarkGray))
            ]).block(Block::default().borders(Borders::ALL).title("Live Logs"));
            f.render_widget(logs, chunks[2]);
        })?;
        Ok(())
    }

    pub fn cleanup(&mut self) -> io::Result<()> {
        disable_raw_mode()?;
        execute!(self.terminal.backend_mut(), LeaveAlternateScreen)?;
        self.terminal.show_cursor()?;
        Ok(())
    }
}
