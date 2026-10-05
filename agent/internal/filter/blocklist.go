package filter

import (
	"bufio"
	"os"
	"strings"
	"sync"
)

// Blocklist maps a domain to a category. Lookups also match parent domains, so
// a single entry "example.com adult" blocks "cdn.example.com" too.
type Blocklist struct {
	mu      sync.RWMutex
	domains map[string]string // fully-qualified domain -> category
}

func NewBlocklist() *Blocklist {
	return &Blocklist{domains: map[string]string{}}
}

// Load replaces the list from a file. Each non-empty, non-# line is:
//
//	<category> <domain>
//
// e.g.  adult  example.com
func (b *Blocklist) Load(path string) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()

	next := map[string]string{}
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		category := strings.ToLower(fields[0])
		domain := strings.ToLower(strings.TrimSuffix(fields[1], "."))
		next[domain] = category
	}
	if err := sc.Err(); err != nil {
		return err
	}

	b.mu.Lock()
	b.domains = next
	b.mu.Unlock()
	return nil
}

// Match returns the category if name or any of its parent domains is listed.
func (b *Blocklist) Match(name string) (category string, blocked bool) {
	name = strings.ToLower(strings.TrimSuffix(name, "."))
	b.mu.RLock()
	defer b.mu.RUnlock()

	labels := strings.Split(name, ".")
	for i := 0; i < len(labels)-1; i++ {
		candidate := strings.Join(labels[i:], ".")
		if cat, ok := b.domains[candidate]; ok {
			return cat, true
		}
	}
	return "", false
}

func (b *Blocklist) Size() int {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return len(b.domains)
}
