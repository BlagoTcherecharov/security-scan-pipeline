/**
 * @param {string | undefined} name
 * @returns {string}
 */
export function greeting(name) {
  const who = name && name.trim() ? name.trim() : "world";
  return `Hello, ${who} (frontend)`;
}
