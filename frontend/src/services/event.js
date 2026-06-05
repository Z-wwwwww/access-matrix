import request from './request'

/**
 * Platform-ops domain-event console REST surface. All calls land on
 * /platform/events/..., gated by platform:event:* (PLATFORM_ADMIN only).
 */
export const listEventsApi          = (params) => request.get('/platform/events', { params })
export const getEventApi            = (id)     => request.get(`/platform/events/${id}`)
export const redriveEventApi        = (id)     => request.post(`/platform/events/${id}/redrive`)
export const redriveFailedEventsApi = ()       => request.post('/platform/events/redrive-failed')
