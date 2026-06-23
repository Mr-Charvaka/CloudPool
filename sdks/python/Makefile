# CloudPool Python SDK — Build System
# =====================================

.PHONY: all clean build install dev install-dev test lint format typecheck docs examples

all: clean lint test build

build:
	pip install build
	python -m build

install:
	pip install -e .

install-dev:
	pip install -e ".[dev,all]"

test:
	python -m pytest tests/ -v --cov=cloudpool --cov-report=term-missing

test-ci:
	python -m pytest tests/ -v --cov=cloudpool --cov-report=xml --cov-report=term

lint:
	ruff check cloudpool/ tests/ examples/

format:
	ruff format cloudpool/ tests/ examples/

typecheck:
	mypy cloudpool/

clean:
	rm -rf build/ dist/ *.egg-info/ .coverage coverage.xml coverage.html
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete

docs:
	cd docs && sphinx-build -b html . _build

examples:
	python examples/quickstart.py

release: lint test build
	python -m twine upload dist/*

tox:
	tox
