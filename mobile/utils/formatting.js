/**
 * Utility functions for formatting data for display
 */

/**
 * Format date string for display
 * 
 * @param {string} dateStr - Date string to format
 * @returns {string} Formatted date string
 */
export function formatDisplayDate(dateStr) {
  if (!dateStr) return 'Unknown date';
  try {
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return dateStr;
    return date.toLocaleDateString('en-US', { 
      month: 'long', 
      day: 'numeric', 
      year: 'numeric' 
    });
  } catch {
    return dateStr;
  }
}

/**
 * Get initials from a name
 * 
 * @param {string} name - Full name
 * @returns {string} Initials (e.g., "John Doe" -> "JD")
 */
export function getInitials(name) {
  if (!name) return '?';
  const parts = name.trim().split(' ');
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return name.substring(0, 2).toUpperCase();
}

/**
 * Get avatar color based on name
 * 
 * @param {string} name - Name to generate color for
 * @returns {string} Hex color code
 */
export function getAvatarColor(name) {
  const colors = ['#EF4444', '#F59E0B', '#10B981', '#3B82F6', '#8B5CF6', '#EC4899'];
  const index = (name || '').length % colors.length;
  return colors[index];
}






