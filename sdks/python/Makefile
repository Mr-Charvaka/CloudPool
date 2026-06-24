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
	python -c "import shutil, os; [shutil.rmtree(p, ignore_errors=True) for p in ['build', 'dist', '.coverage', 'coverage.xml', 'coverage.html']]; [os.remove(f) for f in ['__pycache__'] if os.path.exists(f)]"
	python -c "import os; [os.remove(os.path.join(r, f)) for r, _, fs in os.walk('.') for f in fs if f.endswith('.pyc')]" 2>nul || python -c ""
	python -c "import shutil; [shutil.rmtree(os.path.join(r, d), ignore_errors=True) for r, ds, _ in os.walk('.') for d in ds if d == '__pycache__']" 2>nul || true
	python -c "import glob, os; [os.remove(f) for f in glob.glob('*.egg-info', recursive=False)]" 2>nul || true

docs:
	cd docs && sphinx-build -b html . _build

examples:
	python examples/quickstart.py

release: lint test build
	python -m twine upload dist/*

tox:
	tox
