import type { CommunityThread, CommunityComment, CommunityThreadReadState } from '../../api/communityClient'
import type { AuthUser } from '../../api/authClient'
import { CommunityCommentSection } from './CommunityCommentSection'
import { CommunityThreadHeader } from './CommunityThreadHeader'

interface CommunityThreadCardProps {
  activeThread: CommunityThread | null
  readState: CommunityThreadReadState | null
  activeTarget: { targetType: string; targetId: string; label: string } | null
  selectedSatelliteName: string | null
  comments: CommunityComment[]
  commentsLoading: boolean
  commentsError: string
  authUser: AuthUser
  replyToComment: CommunityComment | null
  editingCommentId: number | null
  editingBody: string
  postingComment: boolean
  newCommentBody: string
  setReplyToComment: (c: CommunityComment | null) => void
  setEditingCommentId: (id: number | null) => void
  setEditingBody: (body: string) => void
  setNewCommentBody: (body: string) => void
  handleToggleLike: (threadId: number) => void
  handleSubmitComment: () => void
  handleSaveEdit: (commentId: number) => void
  handleDeleteComment: (commentId: number) => void
  handleReportComment: (commentId: number) => void
  handleResumeReading: () => void
  handleGoToLatest: () => void
  ensureThread: (targetType: string, targetId: string) => void
  onFocusSatellite: (satelliteId: number) => void
}

export function CommunityThreadCard({
  activeThread,
  readState,
  activeTarget,
  selectedSatelliteName,
  comments,
  commentsLoading,
  commentsError,
  authUser,
  replyToComment,
  editingCommentId,
  editingBody,
  postingComment,
  newCommentBody,
  setReplyToComment,
  setEditingCommentId,
  setEditingBody,
  setNewCommentBody,
  handleToggleLike,
  handleSubmitComment,
  handleSaveEdit,
  handleDeleteComment,
  handleReportComment,
  handleResumeReading,
  handleGoToLatest,
  ensureThread,
  onFocusSatellite,
}: CommunityThreadCardProps) {
  return (
    <div className="community-thread-card">
      <CommunityThreadHeader
        activeThread={activeThread}
        readState={readState}
        activeTarget={activeTarget}
        selectedSatelliteName={selectedSatelliteName}
        commentsCount={comments.length}
        commentsLoading={commentsLoading}
        handleToggleLike={handleToggleLike}
        handleResumeReading={handleResumeReading}
        handleGoToLatest={handleGoToLatest}
        ensureThread={ensureThread}
        onFocusSatellite={onFocusSatellite}
      />

      {commentsError ? <p className="community-error">{commentsError}</p> : null}
      {!activeThread && commentsLoading ? <p className="updated-at">Caricamento thread...</p> : null}

      {activeThread ? (
        <CommunityCommentSection
          comments={comments}
          commentsLoading={commentsLoading}
          authUser={authUser}
          replyToComment={replyToComment}
          editingCommentId={editingCommentId}
          editingBody={editingBody}
          postingComment={postingComment}
          newCommentBody={newCommentBody}
          setReplyToComment={setReplyToComment}
          setEditingCommentId={setEditingCommentId}
          setEditingBody={setEditingBody}
          setNewCommentBody={setNewCommentBody}
          handleSubmitComment={handleSubmitComment}
          handleSaveEdit={handleSaveEdit}
          handleDeleteComment={handleDeleteComment}
          handleReportComment={handleReportComment}
        />
      ) : null}
    </div>
  )
}
