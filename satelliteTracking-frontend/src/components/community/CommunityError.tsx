/**
 * Visualizza errori o messaggi di stato.
 * Props:
 * - message: stringa di errore o stato
 */
export function CommunityError({ message }: { message: string }) {
  if (!message) return null
  return <div className="community-error">{message}</div>
}
