package idgen

import "testing"

// MyBatisPlusBitLayout 是三端约定的位布局基准值：
// ts = epoch + 1000ms, datacenter = 0, worker = 5, sequence = 0
const MyBatisPlusBitLayout int64 = 4194324480

func TestBitLayoutMatchesMybatisPlus(t *testing.T) {
	got := int64(1000)<<22 | int64(0)<<17 | int64(5)<<12 | int64(0)
	if got != MyBatisPlusBitLayout {
		t.Fatalf("bit layout mismatch: got %d, want %d", got, MyBatisPlusBitLayout)
	}
}

func TestInitRejectsOutOfRangeNodeID(t *testing.T) {
	if err := Init(-1); err == nil {
		t.Fatal("expected error for node id -1")
	}
	if err := Init(MaxNodeID + 1); err == nil {
		t.Fatal("expected error for node id 1024")
	}
}

func TestNextEmbedsConfiguredNodeIDAndIncreases(t *testing.T) {
	if err := Init(32); err != nil {
		t.Fatalf("init failed: %v", err)
	}
	first := Next()
	second := Next()
	if first <= 0 || second <= 0 {
		t.Fatalf("expected positive ids, got %d and %d", first, second)
	}
	if second <= first {
		t.Fatalf("expected increasing ids, got %d then %d", first, second)
	}
	if got := CurrentNodeID(); got != 32 {
		t.Fatalf("expected node id 32, got %d", got)
	}
}

func TestNextReturnsZeroWhenNotInitialized(t *testing.T) {
	node = nil
	if got := Next(); got != 0 {
		t.Fatalf("expected 0 before init, got %d", got)
	}
}
