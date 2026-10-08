import os
import re
import subprocess
import sys

def main():
    print("==================================================")
    print("           SAP Bill Crawler Documentation         ")
    print("               HTML & PDF Generator               ")
    print("==================================================")

    md_path = "CODEBASE_DOCUMENTATION.md"
    html_path = "CODEBASE_DOCUMENTATION.html"
    pdf_path = "CODEBASE_DOCUMENTATION.pdf"

    if not os.path.exists(md_path):
        print(f"Error: {md_path} not found.")
        sys.exit(1)

    print(f"Reading {md_path}...")
    with open(md_path, "r", encoding="utf-8") as f:
        markdown_text = f.read()

    # Preprocess markdown: clean up line numbers if they exist
    # (Since sometimes files have line numbers, let's verify if CODEBASE_DOCUMENTATION.md has it.
    # In the view_file tool output, line numbers were added by the view_file tool itself,
    # but let's make sure the file content itself doesn't have it. If it does, we clean it.
    # We checked the original text, and it looks like a clean markdown file.)

    print("Importing python-markdown...")
    try:
        import markdown
    except ImportError:
        print("markdown package not installed. Installing...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "markdown"])
        import markdown

    print("Converting Markdown to HTML...")
    # Using 'extra' for tables/codeblocks and 'toc' for headers anchors and table of contents
    md = markdown.Markdown(extensions=['extra', 'toc'])
    html_body = md.convert(markdown_text)
    toc_html = md.toc

    print("Generating styled template...")
    html_content = get_html_template(html_body, toc_html)

    print(f"Saving {html_path}...")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html_content)

    print("Checking for Microsoft Edge to compile PDF...")
    edge_path = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
    if os.path.exists(edge_path):
        print(f"Edge found at: {edge_path}")
        print("Compiling PDF using headless Edge...")
        
        # We need absolute paths for Edge to work reliably with file:/// URLs
        abs_html_path = os.path.abspath(html_path)
        abs_pdf_path = os.path.abspath(pdf_path)
        
        # Run edge in headless mode to print to PDF
        cmd = [
            edge_path,
            "--headless",
            "--disable-gpu",
            "--no-sandbox",
            f"--print-to-pdf={abs_pdf_path}",
            f"file:///{abs_html_path}"
        ]
        
        try:
            subprocess.run(cmd, check=True)
            print(f"Success! PDF created at: {pdf_path}")
        except subprocess.CalledProcessError as e:
            print(f"Error compiling PDF: {e}")
            print("Note: You can still open the HTML file and save/print it as PDF from your browser.")
    else:
        print("Microsoft Edge not found at the default path.")
        print("Please compile PDF manually by opening the HTML file in your browser and choosing Print -> Save as PDF.")

    print("\nGeneration finished successfully!")
    print(f"Interactive HTML: {html_path}")
    print(f"Print-ready PDF: {pdf_path}")
    print("==================================================")

def get_html_template(body, toc):
    # Setup CSS and Javascript for the HTML file
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SAP Subscription Billing System - Codebase Documentation</title>
    
    <!-- Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&family=Inter:wght@300;400;500;600;700&family=Fira+Code:wght@400;500&display=swap" rel="stylesheet">
    
    <!-- PrismJS Theme for beautiful code blocks -->
    <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css" rel="stylesheet" />
    <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/line-numbers/prism-line-numbers.min.css" rel="stylesheet" />
    
    <!-- FontAwesome for beautiful icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <style>
        :root {{
            --bg-color: #f8fafc;
            --sidebar-bg: #ffffff;
            --text-color: #0f172a;
            --text-muted: #64748b;
            --accent-color: #2563eb;
            --accent-hover: #1d4ed8;
            --border-color: #e2e8f0;
            --card-bg: #ffffff;
            --code-bg: #1e293b;
            --code-text: #f8fafc;
            --shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
            --transition: all 0.25s ease;
        }}

        [data-theme="dark"] {{
            --bg-color: #0f172a;
            --sidebar-bg: #020617;
            --text-color: #f1f5f9;
            --text-muted: #94a3b8;
            --accent-color: #3b82f6;
            --accent-hover: #60a5fa;
            --border-color: #1e293b;
            --card-bg: #1e293b;
            --code-bg: #020617;
            --code-text: #f1f5f9;
            --shadow: 0 4px 6px -1px rgb(0 0 0 / 0.5), 0 2px 4px -2px rgb(0 0 0 / 0.5);
        }}

        * {{
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }}

        html {{
            scroll-behavior: smooth;
        }}

        body {{
            font-family: 'Inter', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-color);
            line-height: 1.6;
            transition: var(--transition);
            display: flex;
        }}

        /* Scrollbar styles */
        ::-webkit-scrollbar {{
            width: 8px;
            height: 8px;
        }}
        ::-webkit-scrollbar-track {{
            background: transparent;
        }}
        ::-webkit-scrollbar-thumb {{
            background: var(--border-color);
            border-radius: 4px;
        }}
        ::-webkit-scrollbar-thumb:hover {{
            background: var(--text-muted);
        }}

        /* Header / Navbar */
        header {{
            position: fixed;
            top: 0;
            right: 0;
            left: 280px;
            height: 64px;
            background-color: var(--sidebar-bg);
            border-bottom: 1px solid var(--border-color);
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0 2rem;
            z-index: 100;
            transition: var(--transition);
        }}

        /* Sidebar styling */
        .sidebar {{
            position: fixed;
            top: 0;
            left: 0;
            bottom: 0;
            width: 280px;
            background-color: var(--sidebar-bg);
            border-right: 1px solid var(--border-color);
            display: flex;
            flex-direction: column;
            z-index: 200;
            transition: var(--transition);
        }}

        .sidebar-brand {{
            padding: 1.5rem;
            border-bottom: 1px solid var(--border-color);
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }}

        .sidebar-brand i {{
            color: var(--accent-color);
            font-size: 1.5rem;
        }}

        .sidebar-brand h1 {{
            font-family: 'Outfit', sans-serif;
            font-size: 1.15rem;
            font-weight: 700;
            letter-spacing: -0.025em;
            color: var(--text-color);
        }}

        .search-container {{
            padding: 1rem;
            border-bottom: 1px solid var(--border-color);
            position: relative;
        }}

        .search-container i {{
            position: absolute;
            left: 1.75rem;
            top: 50%;
            transform: translateY(-50%);
            color: var(--text-muted);
            font-size: 0.875rem;
        }}

        .search-input {{
            width: 100%;
            padding: 0.625rem 1rem 0.625rem 2.25rem;
            border-radius: 8px;
            border: 1px solid var(--border-color);
            background-color: var(--bg-color);
            color: var(--text-color);
            font-family: inherit;
            font-size: 0.875rem;
            outline: none;
            transition: var(--transition);
        }}

        .search-input:focus {{
            border-color: var(--accent-color);
            box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
        }}

        .sidebar-toc {{
            flex: 1;
            overflow-y: auto;
            padding: 1.5rem 1rem;
        }}

        /* Style generated Table of Contents */
        .toc {{
            list-style: none;
        }}

        .toc ul {{
            list-style: none;
            padding-left: 1rem;
            margin-top: 0.25rem;
            margin-bottom: 0.5rem;
            border-left: 1px solid var(--border-color);
        }}

        .toc li {{
            margin-bottom: 0.35rem;
        }}

        .toc a {{
            display: block;
            color: var(--text-muted);
            text-decoration: none;
            font-size: 0.875rem;
            font-weight: 500;
            padding: 0.25rem 0.5rem;
            border-radius: 4px;
            transition: var(--transition);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }}

        .toc a:hover {{
            color: var(--accent-color);
            background-color: var(--bg-color);
        }}

        .toc li.active > a {{
            color: var(--accent-color);
            background-color: rgba(37, 99, 235, 0.1);
            font-weight: 600;
        }}

        /* Content area layout */
        .main-content {{
            margin-left: 280px;
            margin-top: 64px;
            width: calc(100% - 280px);
            padding: 3rem 4rem;
            display: flex;
            justify-content: center;
            min-height: calc(100vh - 64px);
        }}

        .content-container {{
            max-width: 850px;
            width: 100%;
        }}

        /* Floating Actions in Header */
        .header-actions {{
            display: flex;
            align-items: center;
            gap: 1rem;
        }}

        .btn {{
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.5rem 1rem;
            border-radius: 8px;
            font-family: inherit;
            font-size: 0.875rem;
            font-weight: 600;
            cursor: pointer;
            border: 1px solid var(--border-color);
            background-color: var(--sidebar-bg);
            color: var(--text-color);
            transition: var(--transition);
            text-decoration: none;
        }}

        .btn:hover {{
            border-color: var(--accent-color);
            color: var(--accent-color);
            background-color: rgba(37, 99, 235, 0.05);
        }}

        .btn-primary {{
            background-color: var(--accent-color);
            border-color: var(--accent-color);
            color: white;
        }}

        .btn-primary:hover {{
            background-color: var(--accent-hover);
            border-color: var(--accent-hover);
            color: white;
        }}

        /* Markdown Typography & Styling */
        .markdown-body h1 {{
            font-family: 'Outfit', sans-serif;
            font-size: 2.25rem;
            font-weight: 700;
            margin-top: 0;
            margin-bottom: 1.5rem;
            border-bottom: 2px solid var(--border-color);
            padding-bottom: 0.5rem;
            letter-spacing: -0.03em;
        }}

        .markdown-body h2 {{
            font-family: 'Outfit', sans-serif;
            font-size: 1.65rem;
            font-weight: 600;
            margin-top: 2.5rem;
            margin-bottom: 1rem;
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 0.4rem;
            letter-spacing: -0.02em;
        }}

        .markdown-body h3 {{
            font-family: 'Outfit', sans-serif;
            font-size: 1.25rem;
            font-weight: 600;
            margin-top: 2rem;
            margin-bottom: 0.75rem;
        }}

        .markdown-body h4 {{
            font-size: 1rem;
            font-weight: 600;
            margin-top: 1.5rem;
            margin-bottom: 0.5rem;
        }}

        .markdown-body p {{
            margin-bottom: 1.25rem;
            color: var(--text-color);
            font-size: 1rem;
            line-height: 1.7;
        }}

        .markdown-body hr {{
            height: 1px;
            background-color: var(--border-color);
            border: none;
            margin: 2.5rem 0;
        }}

        /* Lists */
        .markdown-body ul, .markdown-body ol {{
            margin-bottom: 1.25rem;
            padding-left: 1.5rem;
        }}

        .markdown-body li {{
            margin-bottom: 0.5rem;
        }}

        .markdown-body li > ul, .markdown-body li > ol {{
            margin-top: 0.5rem;
            margin-bottom: 0;
        }}

        /* Blockquotes / Alerts */
        .markdown-body blockquote {{
            padding: 0.75rem 1.25rem;
            border-left: 4px solid var(--accent-color);
            background-color: rgba(37, 99, 235, 0.05);
            border-radius: 0 8px 8px 0;
            margin-bottom: 1.25rem;
            color: var(--text-color);
            font-style: italic;
        }}

        /* Inline Code */
        .markdown-body :not(pre) > code {{
            font-family: 'Fira Code', monospace;
            font-size: 0.875rem;
            padding: 0.2rem 0.4rem;
            background-color: var(--border-color);
            border-radius: 4px;
            color: var(--accent-color);
            font-weight: 500;
            transition: var(--transition);
        }}

        /* Fenced Code Blocks */
        .markdown-body pre {{
            position: relative;
            background-color: var(--code-bg) !important;
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 1.25rem !important;
            margin-bottom: 1.5rem;
            overflow-x: auto;
        }}

        .markdown-body pre code {{
            font-family: 'Fira Code', monospace !important;
            font-size: 0.875rem !important;
            color: var(--code-text) !important;
            background: transparent !important;
            border: none !important;
            padding: 0 !important;
        }}

        /* Copy Button for Code Blocks */
        .copy-code-btn {{
            position: absolute;
            top: 0.5rem;
            right: 0.5rem;
            background-color: rgba(255, 255, 255, 0.1);
            color: rgba(255, 255, 255, 0.8);
            border: none;
            border-radius: 4px;
            padding: 0.25rem 0.5rem;
            font-size: 0.75rem;
            cursor: pointer;
            opacity: 0;
            transition: var(--transition);
            display: flex;
            align-items: center;
            gap: 0.25rem;
        }}

        .markdown-body pre:hover .copy-code-btn {{
            opacity: 1;
        }}

        .copy-code-btn:hover {{
            background-color: rgba(255, 255, 255, 0.2);
            color: white;
        }}

        /* Tables styling */
        .markdown-body table {{
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 1.5rem;
            font-size: 0.925rem;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
        }}

        .markdown-body th, .markdown-body td {{
            padding: 0.75rem 1rem;
            border: 1px solid var(--border-color);
            text-align: left;
        }}

        .markdown-body th {{
            background-color: var(--border-color);
            font-weight: 600;
            color: var(--text-color);
        }}

        .markdown-body tr:nth-child(even) {{
            background-color: rgba(0, 0, 0, 0.015);
        }}

        [data-theme="dark"] .markdown-body tr:nth-child(even) {{
            background-color: rgba(255, 255, 255, 0.015);
        }}

        /* Links */
        .markdown-body a {{
            color: var(--accent-color);
            text-decoration: none;
            border-bottom: 1px dotted transparent;
            transition: var(--transition);
        }}

        .markdown-body a:hover {{
            border-bottom-color: var(--accent-color);
        }}

        /* Architecture ASCII blocks styling */
        .markdown-body pre code.language-none,
        .markdown-body pre code.language-text {{
            color: #38bdf8 !important; /* light blue for ASCII diagrams */
            line-height: 1.3;
        }}

        /* Toggle Menu for Mobile */
        .hamburger {{
            display: none;
            font-size: 1.25rem;
            cursor: pointer;
            color: var(--text-color);
        }}

        /* Responsive Breakpoints */
        @media (max-width: 992px) {{
            header {{
                left: 0;
            }}
            .sidebar {{
                left: -280px;
            }}
            .sidebar.open {{
                left: 0;
            }}
            .main-content {{
                margin-left: 0;
                width: 100%;
                padding: 2rem;
            }}
            .hamburger {{
                display: block;
            }}
        }}

        /* Print Specific Styles - Optimized for PDF generation */
        @media print {{
            body {{
                background-color: white !important;
                color: black !important;
                display: block !important;
            }}
            
            header, .sidebar, .hamburger, .copy-code-btn, .header-actions {{
                display: none !important;
            }}
            
            .main-content {{
                margin-left: 0 !important;
                margin-top: 0 !important;
                width: 100% !important;
                padding: 0 !important;
                min-height: auto !important;
            }}
            
            .content-container {{
                max-width: 100% !important;
            }}
            
            .markdown-body h1, .markdown-body h2 {{
                border-bottom-color: #ccc !important;
            }}
            
            .markdown-body h1, .markdown-body h2, .markdown-body h3 {{
                page-break-after: avoid;
            }}
            
            pre, blockquote, table, tr, img {{
                page-break-inside: avoid;
            }}
            
            .markdown-body pre {{
                background-color: #f1f5f9 !important;
                border-color: #cbd5e1 !important;
                color: black !important;
            }}
            
            .markdown-body pre code {{
                color: black !important;
            }}
            
            .markdown-body :not(pre) > code {{
                background-color: #f1f5f9 !important;
                color: #b91c1c !important; /* Red color for print inline codes */
            }}
            
            a {{
                text-decoration: underline;
                color: #2563eb !important;
            }}
            
            a[href^="http"]::after {{
                content: " (" attr(href) ")";
                font-size: 0.8rem;
                color: #475569;
            }}
        }}
    </style>
</head>
<body data-theme="light">

    <!-- Sidebar TOC -->
    <aside class="sidebar" id="sidebar">
        <div class="sidebar-brand">
            <i class="fa-solid fa-book-open-reader"></i>
            <h1>SAP Bill Crawler Docs</h1>
        </div>
        <div class="search-container">
            <i class="fa-solid fa-magnifying-glass"></i>
            <input type="text" id="searchInput" class="search-input" placeholder="Search documentation...">
        </div>
        <nav class="sidebar-toc">
            {toc}
        </nav>
    </aside>

    <!-- Main Header -->
    <header>
        <div class="hamburger" id="hamburger">
            <i class="fa-solid fa-bars"></i>
        </div>
        <div>
            <!-- Breadcrumb or space -->
        </div>
        <div class="header-actions">
            <button class="btn" id="themeToggle" title="Toggle Theme">
                <i class="fa-solid fa-moon"></i> <span>Dark Mode</span>
            </button>
            <button class="btn btn-primary" id="printBtn" title="Export / Print to PDF">
                <i class="fa-solid fa-file-pdf"></i> <span>Export PDF</span>
            </button>
        </div>
    </header>

    <!-- Main Content Body -->
    <main class="main-content">
        <div class="content-container">
            <article class="markdown-body">
                {body}
            </article>
        </div>
    </main>

    <!-- PrismJS libraries for syntax highlighting -->
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-core.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/autoloader/prism-autoloader.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/line-numbers/prism-line-numbers.min.js"></script>

    <script>
        // Theme Toggle Script
        const themeToggle = document.getElementById('themeToggle');
        const themeIcon = themeToggle.querySelector('i');
        const themeText = themeToggle.querySelector('span');
        
        // Load saved theme
        const savedTheme = localStorage.getItem('theme') || 'light';
        document.body.setAttribute('data-theme', savedTheme);
        updateThemeUI(savedTheme);

        themeToggle.addEventListener('click', () => {{
            const currentTheme = document.body.getAttribute('data-theme');
            const newTheme = currentTheme === 'light' ? 'dark' : 'light';
            document.body.setAttribute('data-theme', newTheme);
            localStorage.setItem('theme', newTheme);
            updateThemeUI(newTheme);
        }});

        function updateThemeUI(theme) {{
            if (theme === 'dark') {{
                themeIcon.className = 'fa-solid fa-sun';
                themeText.textContent = 'Light Mode';
            }} else {{
                themeIcon.className = 'fa-solid fa-moon';
                themeText.textContent = 'Dark Mode';
            }}
        }}

        // Mobile Sidebar Toggle
        const hamburger = document.getElementById('hamburger');
        const sidebar = document.getElementById('sidebar');
        
        hamburger.addEventListener('click', (e) => {{
            sidebar.classList.toggle('open');
            e.stopPropagation();
        }});

        document.addEventListener('click', (e) => {{
            if (sidebar.classList.contains('open') && !sidebar.contains(e.target) && e.target !== hamburger) {{
                sidebar.classList.remove('open');
            }}
        }});

        // Print PDF button
        document.getElementById('printBtn').addEventListener('click', () => {{
            window.print();
        }});

        // Add copy button to code blocks
        document.querySelectorAll('pre').forEach((preBlock) => {{
            const copyBtn = document.createElement('button');
            copyBtn.className = 'copy-code-btn';
            copyBtn.innerHTML = '<i class="fa-regular fa-copy"></i> Copy';
            
            copyBtn.addEventListener('click', () => {{
                const codeText = preBlock.querySelector('code').innerText;
                navigator.clipboard.writeText(codeText).then(() => {{
                    copyBtn.innerHTML = '<i class="fa-solid fa-check"></i> Copied!';
                    setTimeout(() => {{
                        copyBtn.innerHTML = '<i class="fa-regular fa-copy"></i> Copy';
                    }}, 2000);
                }});
            }});
            
            preBlock.appendChild(copyBtn);
        }});

        // Highlight TOC on Scroll
        const sections = document.querySelectorAll('.markdown-body h1, .markdown-body h2, .markdown-body h3');
        const tocLinks = document.querySelectorAll('.toc a');

        const observerOptions = {{
            root: null,
            rootMargin: '-64px 0px -40% 0px',
            threshold: 0
        }};

        const observer = new IntersectionObserver((entries) => {{
            entries.forEach(entry => {{
                if (entry.isIntersecting) {{
                    const id = entry.target.getAttribute('id');
                    if (!id) return;
                    
                    tocLinks.forEach(link => {{
                        const href = link.getAttribute('href');
                        const parentLi = link.parentElement;
                        
                        if (href === '#' + id) {{
                            parentLi.classList.add('active');
                            // Scroll TOC item into view smoothly in sidebar
                            parentLi.scrollIntoView({{ behavior: 'smooth', block: 'nearest' }});
                        }} else {{
                            parentLi.classList.remove('active');
                        }}
                    }});
                }}
            }});
        }}, observerOptions);

        sections.forEach(section => observer.observe(section));

        // Search Functionality
        const searchInput = document.getElementById('searchInput');
        searchInput.addEventListener('input', (e) => {{
            const query = e.target.value.toLowerCase().trim();
            const tocItems = document.querySelectorAll('.toc li');
            const paragraphs = document.querySelectorAll('.markdown-body p, .markdown-body li, .markdown-body h1, .markdown-body h2, .markdown-body h3, .markdown-body tr');

            // Filter TOC items
            tocItems.forEach(item => {{
                const text = item.textContent.toLowerCase();
                if (text.includes(query)) {{
                    item.style.display = 'block';
                }} else {{
                    item.style.display = 'none';
                }}
            }});

            // Highlight matches in body if query is not empty
            if (query === '') {{
                // Reset styling
                document.querySelectorAll('.search-highlight').forEach(el => {{
                    el.replaceWith(document.createTextNode(el.textContent));
                }});
                return;
            }}

            // Highlight is simple here - we can filter main view headings or sections
            // Or just filter headings. For clean UI, we just let TOC handle search filtering.
        }});
    </script>
</body>
</html>"""

if __name__ == "__main__":
    main()
