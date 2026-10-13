GOOS ?= windows
GOARCH ?= amd64

.PHONY: all test clean

all:
	CGO_ENABLED=0 GOOS=$(GOOS) GOARCH=$(GOARCH) go build -trimpath -ldflags "-s -w -H windowsgui" -o dist/spoty-t9-$(GOOS)-$(GOARCH).exe .

test:
	go test ./...

clean:
	rm -rf dist
