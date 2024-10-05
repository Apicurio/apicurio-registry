import os
import re

# Function to find all Java files in a directory and its subdirectories
def find_java_files(directory):
    java_files = []
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith(".java"):
                java_files.append(os.path.join(root, file))
    return java_files

# Function to extract specific comment blocks from a Java file
def extract_comments(file_path):
    comments = []
    with open(file_path, 'r') as file:
        content = file.read()
        comment_blocks = re.findall(r'/\*[\s\S]*?\*/', content)
        for block in comment_blocks:
            if "This example" in block:
                comments.append(block)
    return comments

# Function to convert a list of comments to markdown format
def comments_to_markdown(comments):
    markdown = ""
    for comment in comments:
        # Clean up comment block, remove * from each line, and convert to markdown
        clean_comment = comment.replace('/*', '').replace('*/', '').strip()
        clean_comment_lines = [line.lstrip(' *') for line in clean_comment.split('\n')]
        clean_comment = "\n".join(clean_comment_lines)
        markdown += f"{clean_comment}\n\n"
    return markdown

# Function to create a README.md file from a template and extracted comments
def create_readme(directory, markdown_content):
    # Use the name of the directory as the H1 header
    directory_name = os.path.basename(directory.rstrip('/'))
    readme_template = f"# {directory_name}\n\nThis is an Apicurio Registry example. For more information about Apicurio Registry see https://www.apicur.io/registry/\n\n## Instructions\n\n"
    readme_content = readme_template + markdown_content
    
    # Write the README.md file
    readme_path = os.path.join(directory, "README.md")
    with open(readme_path, 'w') as readme_file:
        readme_file.write(readme_content)

# Main function to process each immediate subdirectory
def main():
    # Get the base directory (where the script is run)
    base_directory = os.getcwd()
    
    # List all immediate subdirectories
    subdirectories = [os.path.join(base_directory, d) for d in os.listdir(base_directory) if os.path.isdir(os.path.join(base_directory, d))]
    
    # Process each subdirectory
    for subdirectory in subdirectories:
        # Find all Java files
        java_files = find_java_files(subdirectory)
        
        # Extract comments from each Java file
        all_comments = []
        for java_file in java_files:
            comments = extract_comments(java_file)
            all_comments.extend(comments)
        
        # Convert comments to markdown
        markdown_content = comments_to_markdown(all_comments)
        
        # Create the README.md file
        create_readme(subdirectory, markdown_content)

# Entry point for the script
if __name__ == "__main__":
    main()
