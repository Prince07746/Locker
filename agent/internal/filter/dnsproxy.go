package filter

import (
	"log"
	"net"
	"sync"
	"time"

	"github.com/miekg/dns"
)

// Reporter is satisfied by the backend client.
type Reporter interface {
	ReportCategory(category string)
}

// Proxy is a local resolver. The system's DNS is pointed at it; blocked names
// are answered with 0.0.0.0 (and reported), everything else is forwarded.
type Proxy struct {
	listenAddr  string
	upstream    string
	list        *Blocklist
	reporter    Reporter
	client      *dns.Client
	udp, tcp    *dns.Server

	mu       sync.Mutex
	reported map[string]time.Time // debounce repeated reports per domain
}

const reportCooldown = 5 * time.Minute

func NewProxy(listenAddr, upstream string, list *Blocklist, reporter Reporter) *Proxy {
	return &Proxy{
		listenAddr: listenAddr,
		upstream:   upstream,
		list:       list,
		reporter:   reporter,
		client:     &dns.Client{Timeout: 5 * time.Second},
		reported:   map[string]time.Time{},
	}
}

func (p *Proxy) handle(w dns.ResponseWriter, req *dns.Msg) {
	if len(req.Question) == 0 {
		dns.HandleFailed(w, req)
		return
	}
	q := req.Question[0]
	name := q.Name

	if cat, blocked := p.list.Match(name); blocked {
		p.maybeReport(name, cat)
		resp := new(dns.Msg)
		resp.SetReply(req)
		switch q.Qtype {
		case dns.TypeAAAA:
			rr, _ := dns.NewRR(name + " 60 IN AAAA ::")
			resp.Answer = append(resp.Answer, rr)
		default:
			rr, _ := dns.NewRR(name + " 60 IN A 0.0.0.0")
			resp.Answer = append(resp.Answer, rr)
		}
		_ = w.WriteMsg(resp)
		return
	}

	// Forward allowed queries upstream.
	resp, _, err := p.client.Exchange(req, p.upstream)
	if err != nil || resp == nil {
		dns.HandleFailed(w, req)
		return
	}
	_ = w.WriteMsg(resp)
}

func (p *Proxy) maybeReport(name, category string) {
	p.mu.Lock()
	last, seen := p.reported[name]
	now := time.Now()
	if seen && now.Sub(last) < reportCooldown {
		p.mu.Unlock()
		return
	}
	p.reported[name] = now
	p.mu.Unlock()

	if p.reporter != nil {
		go p.reporter.ReportCategory(category)
	}
}

// Start begins serving on UDP and TCP. Returns once listeners are bound.
func (p *Proxy) Start() error {
	mux := dns.NewServeMux()
	mux.HandleFunc(".", p.handle)

	udpConn, err := net.ListenPacket("udp", p.listenAddr)
	if err != nil {
		return err
	}
	tcpLn, err := net.Listen("tcp", p.listenAddr)
	if err != nil {
		udpConn.Close()
		return err
	}

	p.udp = &dns.Server{PacketConn: udpConn, Handler: mux}
	p.tcp = &dns.Server{Listener: tcpLn, Handler: mux}

	go func() {
		if err := p.udp.ActivateAndServe(); err != nil {
			log.Printf("dns udp server stopped: %v", err)
		}
	}()
	go func() {
		if err := p.tcp.ActivateAndServe(); err != nil {
			log.Printf("dns tcp server stopped: %v", err)
		}
	}()
	log.Printf("DNS filter listening on %s, upstream %s, %d domains loaded",
		p.listenAddr, p.upstream, p.list.Size())
	return nil
}

func (p *Proxy) Stop() {
	if p.udp != nil {
		_ = p.udp.Shutdown()
	}
	if p.tcp != nil {
		_ = p.tcp.Shutdown()
	}
}
