import { gpsOpsGroup } from './gpsOps'
import { starlinkGroup } from './starlink'
import { stationsGroup } from './stations'
import { weatherGroup } from './weather'

export const satelliteGroupSources = [
  stationsGroup,
  starlinkGroup,
  gpsOpsGroup,
  weatherGroup,
] as const
